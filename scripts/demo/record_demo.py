"""Film the existing Debug UI. No app backdoors, fake auth, or database writes."""
import argparse
import math
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.monsters.mobimon.demo"
REMOTE_XML = "/data/local/tmp/mobimon-filming.xml"


class DemoError(Exception):
    pass


def bounds(node):
    values = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
    if len(values) != 4 or values[2] <= values[0] or values[3] <= values[1]:
        raise DemoError("화면 요소의 위치를 확인할 수 없습니다.")
    return values


class Demo:
    def __init__(self, args):
        self.args = args
        self.adb = shutil.which(os.environ.get("ADB", "adb"))
        if not self.adb:
            sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
            if not sdk and os.name == "nt":
                sdk = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")
            candidate = os.path.join(sdk or "", "platform-tools", "adb.exe" if os.name == "nt" else "adb")
            if os.path.isfile(candidate):
                self.adb = candidate
        if not self.adb:
            raise DemoError("Android SDK의 adb를 PATH 또는 ADB 환경변수에 지정하세요.")
        self.serial = args.serial or os.environ.get("ANDROID_SERIAL")
        self.root = None
        self.parents = {}

    def command(self, *args, timeout=30):
        cmd = [self.adb] + (["-s", self.serial] if self.serial else []) + list(args)
        result = subprocess.run(cmd, capture_output=True, encoding="utf-8", errors="replace", timeout=timeout)
        if result.returncode:
            # Never print provider/account UI or command output to the filming log.
            raise DemoError("ADB 명령이 실패했습니다. 기기 연결과 화면 상태를 확인하세요.")
        return result.stdout

    def preflight(self):
        devices = self.command("devices")
        ready = re.findall(r"^(\S+)\s+device$", devices, re.M)
        if not self.serial:
            if len(ready) != 1:
                raise DemoError("실행 중인 기기 하나가 필요합니다. 여러 대라면 --serial을 지정하세요.")
            self.serial = ready[0]
        if self.serial not in ready:
            raise DemoError("선택한 기기가 연결되지 않았거나 USB 승인이 필요합니다.")
        if self.command("shell", "getprop", "ro.kernel.qemu").strip() != "1":
            raise DemoError("이 촬영 도구는 에뮬레이터에서만 실행합니다.")
        if "package:" not in self.command("shell", "pm", "path", PACKAGE):
            raise DemoError("MobiMon Debug (.demo) APK를 먼저 설치하세요.")
        if "android.hardware.type.automotive" not in self.command("shell", "pm", "list", "features"):
            raise DemoError("AAOS 에뮬레이터가 필요합니다.")
        sizes = re.findall(r"(?:Physical|Override) size: (\d+x\d+)", self.command("shell", "wm", "size"))
        if not sizes or sizes[-1] != "2560x1440":
            raise DemoError("메뉴 촬영 조작은 프로젝트의 2560x1440 가로 AAOS 배치를 사용합니다.")
        # Menu coordinates also depend on density and font scale.
        densities = re.findall(r"(?:Physical|Override) density: (\d+)", self.command("shell", "wm", "density"))
        font_scale = self.command("shell", "settings", "get", "system", "font_scale").strip()
        if not densities or densities[-1] != "160" or font_scale not in ("null", "1", "1.0"):
            raise DemoError("메뉴 촬영 조작은 화면 밀도 160과 기본 글자 크기를 사용합니다.")
        print(f"준비 확인: {self.serial}, Debug 앱. 앱 데이터는 초기화하지 않습니다.", flush=True)

    def snapshot(self):
        # Remove old output so a failed dump can never reuse an earlier screen.
        for attempt in range(3):
            self.command("shell", "rm", "-f", REMOTE_XML)
            try:
                self.command("shell", "uiautomator", "dump", REMOTE_XML)
                data = self.command("shell", "cat", REMOTE_XML)
                self.root = ET.fromstring(data)
                break
            except (DemoError, ET.ParseError):
                if attempt == 2:
                    raise
                time.sleep(1)
            finally:
                self.command("shell", "rm", "-f", REMOTE_XML)
        self.parents = {child: parent for parent in self.root.iter() for child in parent}
        return self.root

    def matches(self, pattern):
        return [n for n in self.root.iter("node")
                if n.get("package") == PACKAGE and n.get("enabled") == "true"
                and any(re.fullmatch(pattern, n.get(key, "")) for key in ("text", "content-desc"))]

    def find(self, pattern, timeout=20, scroll=False):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            self.snapshot()
            if self.matches("히든 퀘스트 달성!"):
                # Existing milestone notices can cover a destination. Dismiss only;
                # do not collect unrelated rewards while filming the selected quest.
                self.command("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(0.5)
                continue
            hits = self.matches(pattern)
            if hits:
                # A merged parent and its text child can expose the same label.
                return hits[0]
            if scroll:
                self.swipe()
            else:
                time.sleep(0.4)
        raise DemoError(f"화면에서 찾지 못했습니다: {pattern}. 현재 화면에서 멈췄습니다.")

    def clickable(self, node):
        target = node
        while target.get("clickable") != "true" and target in self.parents:
            target = self.parents[target]
        return target if target.get("clickable") == "true" else node

    def tap_node(self, node):
        x1, y1, x2, y2 = bounds(self.clickable(node))
        self.command("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(0.6)

    def tap(self, pattern, **kwargs):
        self.tap_node(self.find(pattern, **kwargs))

    def optional(self, pattern):
        self.snapshot()
        hits = self.matches(pattern)
        if hits:
            self.tap_node(hits[0])
        return bool(hits)

    def swipe(self, up=True):
        regions = [n for n in self.root.iter("node") if n.get("scrollable") == "true" and n.get("package") == PACKAGE]
        if not regions:
            raise DemoError("스크롤 영역이 없습니다. 시나리오의 준비 상태를 확인하세요.")
        # Debug panel is the last scroll container while it is expanded.
        region = regions[-1]
        x1, y1, x2, y2 = bounds(region)
        x = (x1 + x2) // 2
        low, high = y1 + (y2-y1)*3//4, y1 + (y2-y1)//4
        a, b = (low, high) if up else (high, low)
        self.command("shell", "input", "swipe", str(x), str(a), str(x), str(b), "450")

    def section(self, title, expand=True):
        desired, action = ("접기", "펼치기") if expand else ("펼치기", "접기")
        self.snapshot()
        if self.matches(re.escape(title + " " + desired)):
            return
        self.tap(re.escape(title + " " + action), scroll=True, timeout=45)

    def debug(self, expanded):
        self.snapshot()
        wanted = "디버그 창 축소" if expanded else "디버그 창 펼치기"
        if self.matches(wanted):
            return
        self.tap("디버그 창 펼치기" if expanded else "디버그 창 축소")

    def field(self, label, value):
        node = self.find(re.escape(label), scroll=True, timeout=45)
        _, y1, _, y2 = bounds(node)
        edits = [n for n in self.root.iter("node") if n.get("class") == "android.widget.EditText"
                 and n.get("package") == PACKAGE and abs((bounds(n)[1]+bounds(n)[3])/2-(y1+y2)/2) < 50]
        if len(edits) != 1:
            raise DemoError(f"입력란을 유일하게 식별하지 못했습니다: {label}")
        self.tap_node(edits[0])
        self.command("shell", "input", "keycombination", "113", "29")  # Ctrl+A
        self.command("shell", "input", "text", value)
        self.hide_keyboard()
        time.sleep(0.5)

    def hide_keyboard(self):
        # Without a keyboard to consume it, BACK leaves the screen or the app.
        for _ in range(4):
            if "mInputShown=true" in self.command("shell", "dumpsys", "input_method"):
                self.command("shell", "input", "keyevent", "KEYCODE_BACK")
                return
            time.sleep(0.3)

    def automatic_battery(self):
        self.debug(True)
        self.section("차량 상태 (Interpretation)")
        label = self.find("batteryPercent", scroll=True, timeout=45)
        _, y1, _, y2 = bounds(label)
        buttons = [n for n in self.matches("자동")
                   if abs((bounds(n)[1]+bounds(n)[3])/2-(y1+y2)/2) < 30]
        if len(buttons) != 1:
            raise DemoError("배터리의 자동 해석 버튼을 식별하지 못했습니다.")
        self.tap_node(buttons[0])
        self.debug(False)

    def debug_values(self):
        user = self.command("shell", "am", "get-current-user").strip()
        if not user.isdigit():
            raise DemoError("현재 AAOS 사용자를 확인할 수 없습니다.")
        # Read only this Debug simulator file. Never access credentials or databases.
        data = self.command("shell", "run-as", PACKAGE, "--user", user,
                            "cat", "shared_prefs/debug_vss_prefs.xml")
        return {n.get("name"): n.get("value", n.text) for n in ET.fromstring(data)}

    def battery(self, percent):
        label = "Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed"
        for _ in range(3):
            # The field reformats to "1.0" mid-typing; a later "." would save 0.
            self.field(label, str(percent))
            time.sleep(0.7)
            if float(self.debug_values().get(label, "nan")) == percent:
                return
        raise DemoError("배터리 입력값이 반영되지 않았습니다. Debugger 입력란을 확인하세요.")

    def parking_button(self):
        # Non-focusable overlays are absent from uiautomator's active-window tree.
        # Identify our visible TOP END test-control window and use its actual frame.
        windows = self.command("shell", "dumpsys", "window", "windows")
        candidates = []
        for block in re.split(r"\n\s*Window #", windows):
            if (f"package={PACKAGE} " in block and "ty=APPLICATION_OVERLAY" in block
                    and "gr=TOP END" in block and "isVisible=true" in block):
                frame = re.search(r"\bframe=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", block)
                if frame:
                    candidates.append(tuple(map(int, frame.groups())))
        if len(candidates) != 1:
            raise DemoError("표시된 TEST P/D 창을 찾지 못했습니다. 차량 홈 캐릭터와 오버레이 권한을 확인하세요.")
        return candidates[0]

    def toggle_parking(self, expected):
        x1, y1, x2, y2 = self.parking_button()
        self.command("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
        for _ in range(10):
            time.sleep(0.5)
            if self.debug_values().get("Vehicle.Powertrain.Transmission.SelectedGear") == str(expected):
                return
        raise DemoError("주차 테스트 상태가 바뀌지 않았습니다. TEST 버튼을 직접 확인하세요.")

    def home(self):
        for _ in range(5):
            self.snapshot()
            if self.matches("메뉴 열기.*"):
                return
            self.command("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(0.7)
        raise DemoError("홈으로 돌아가지 못했습니다.")

    def route(self, name):
        self.home()
        self.tap("메뉴 열기.*")
        # The focusable AAOS Popup does not reliably expose an idle accessibility
        # tree. Only this menu uses coordinates, verified on the 2560x1440 AVD.
        # Preflight gates geometry; verify the destination before any further action.
        positions = {"홈": 468, "퀘스트": 688, "차량 상태": 802, "꾸미기": 914, "설정": 1026}
        time.sleep(2)
        self.command("shell", "input", "tap", "250", str(positions[name]))
        time.sleep(1)
        if name != "홈":
            self.find(name)

    def hold(self, seconds):
        time.sleep(seconds * self.args.pace)

    def cue(self, text):
        print(f"\n{ text }", flush=True)
        if self.args.step:
            input("이 장면을 시작하려면 Enter: ")

    def prepare(self):
        self.command("shell", "am", "start", "-n", PACKAGE + "/com.monsters.mobimon.MainActivity")
        time.sleep(2)
        self.snapshot()
        if not self.matches("디버그 창 (펼치기|축소)"):
            self.route("설정")
            setting = self.clickable(self.find("Debugger"))
            if setting.get("checked") == "true":
                # Tapping again would turn debug mode off.
                raise DemoError("Debugger가 켜져 있지만 디버그 창이 없습니다. X로 닫았다면 Debugger를 껐다 켜고 P 상태를 확인하세요.")
            self.tap_node(setting)
        self.debug(False)
        self.debug(True)
        self.section("Quest")
        self.tap("P단 정차 설정", scroll=True)
        self.debug(False)
        self.home()
        self.find("주차 확인됨")

    def intro(self):
        self.cue("Scene 1 · 홈 / 주차 상태 · 약 15초")
        self.home()
        self.find("주차 확인됨")
        self.hold(15)

    def vehicle(self):
        self.cue("Scene 2 · 시뮬레이션 배터리 변화 · 조작 구간은 편집으로 줄이세요")
        self.route("차량 상태")
        self.hold(5)
        self.automatic_battery()
        self.debug(True)
        self.section("차량 신호 (VSS)")
        self.section("C. 에너지")
        self.battery(15)
        self.debug(False)
        self.find("15%")
        self.hold(8)
        self.home()
        self.hold(7)
        self.debug(True)
        self.section("차량 신호 (VSS)")
        self.section("C. 에너지")
        self.battery(80)
        self.debug(False)
        self.hold(5)

    def auth(self):
        self.cue("Scene 3 · 실제 GitHub 기기 인증 · 휴대폰 승인이 필요합니다")
        self.route("설정")
        self.tap("연결 안내")
        self.snapshot()
        if self.matches("QR로 연결하기"):
            self.tap("QR로 연결하기")
            self.find("인증 코드|휴대폰에 입력할 코드|승인을 기다리고 있어요", timeout=45)
            print("휴대폰에서 화면의 주소/QR로 승인하세요. 코드와 계정 정보는 로그에 저장하지 않습니다.", flush=True)
        self.find("연결이 완료됐어요\\.|GitHub 계정 인증이 완료됐어요\\.", timeout=self.args.auth_timeout)
        print("계정 인증 화면을 확인했습니다. Copilot 사용 가능 여부는 대화 화면에서 따로 확인합니다.", flush=True)
        self.hold(6)

    def chat(self):
        self.cue("Scene 4 · 실제 Copilot 매뉴얼 질문 / 출처 확인")
        self.home()
        self.tap("대화하기")
        self.find("Copilot 연결됨", timeout=60)
        self.snapshot()
        edits = [n for n in self.root.iter("node") if n.get("package") == PACKAGE and n.get("class") == "android.widget.EditText"]
        if len(edits) != 1 or edits[0].get("text", "").strip():
            raise DemoError("빈 대화 입력란 하나가 필요합니다. 기존 초안은 직접 보관한 뒤 지워 주세요.")
        self.tap_node(edits[0])
        # Android input text reliably supports ASCII without installing another IME.
        question = "What is the recommended tire pressure for IONIQ 5? Use the manual and reply in Korean with sources."
        self.command("shell", "input", "text", question.replace(" ", "%s").replace("?", "\\?"))
        time.sleep(0.5)
        self.snapshot()
        edits = [n for n in self.root.iter("node") if n.get("package") == PACKAGE and n.get("class") == "android.widget.EditText"]
        if len(edits) != 1 or edits[0].get("text", "") != question:
            # The question reaches a real Copilot account; never send a garbled one.
            raise DemoError("질문이 정확히 입력되지 않아 전송하지 않았습니다. 입력란을 비운 뒤 다시 실행하세요.")
        self.hide_keyboard()
        self.hold(3)
        self.tap("메시지 보내기")
        print("응답과 출처를 직접 확인하세요. 답변 내용은 기록하지 않습니다.", flush=True)
        # A human checks this turn's evidence; an older citation is not proof of success.
        input("새 답변과 출처가 확인되면 Enter (오류/출처 없음은 Ctrl+C): ")
        self.hold(15)
        self.home()

    def quest(self):
        self.cue("Scene 5 · 시뮬레이션 퀘스트 보상 → 아이템 구매 → 적용")
        self.home()
        self.debug(True)
        self.section("Quest")
        self.tap("전체 조건 만족 \\(완료 가능\\)", scroll=True, timeout=60)
        self.debug(False)
        self.route("퀘스트")
        self.tap("놓지마 생명줄!", scroll=True)
        self.snapshot()
        if self.matches("보상 수령 완료.*|이미 보상을 받았어요"):
            raise DemoError("이 퀘스트는 이미 수령했습니다. 시나리오의 재촬영 준비를 확인하세요.")
        self.tap("보상 받기")
        self.find("[0-9,]+ P를 받았어요!")
        self.hold(6)
        self.tap("확인")
        self.route("꾸미기")
        self.tap("옷")
        self.tap(re.escape(self.args.item), scroll=True)
        self.hold(4)
        self.snapshot()
        if self.matches("[0-9,]+ P 부족"):
            raise DemoError("포인트가 부족해 아이템을 구매할 수 없습니다. 잔액을 확인하거나 보유 아이템을 고르세요.")
        if self.matches("[0-9,]+ P로 구매하기"):
            self.tap("[0-9,]+ P로 구매하기")
            self.hold(3)
            self.tap("구매하기")
        self.tap("이 모습 적용")
        self.find("착용 중")
        self.hold(5)
        self.home()
        self.hold(6)

    def overlay(self):
        self.cue("Scene 6 · 차량 홈 캐릭터 / 주차 해제 시 퇴장")
        self.home()
        self.find("주차 확인됨")
        self.command("shell", "input", "keyevent", "KEYCODE_HOME")
        time.sleep(3)
        self.parking_button()
        if self.debug_values().get("Vehicle.Powertrain.Transmission.SelectedGear") != "126":
            raise DemoError("오버레이 촬영 시작 상태가 P가 아닙니다.")
        self.hold(8)
        self.toggle_parking(127)
        self.hold(6)
        print("퇴장 장면 촬영 후 Android Studio 녹화를 정지하세요.", flush=True)
        input("녹화가 끝났으면 Enter (P 상태로 복구): ")
        self.toggle_parking(126)


def main():
    parser = argparse.ArgumentParser(description="MobiMon AAOS Debug 촬영 자동화. 녹화/휴대폰 승인은 사람이 진행합니다.")
    parser.add_argument("--serial", help="adb 기기 ID")
    parser.add_argument("--check", action="store_true", help="기기/설치 확인만")
    parser.add_argument("--scene", choices=["all", "intro", "vehicle", "auth", "chat", "quest", "overlay"], default="all")
    parser.add_argument("--step", action="store_true", help="각 장면 시작 전 Enter로 진행")
    parser.add_argument("--item", choices=["모비 고글", "모비 헤드폰", "루나 모자", "루나 선글라스"], default="모비 고글", help="활성 친구와 호환되는 촬영 아이템")
    parser.add_argument("--pace", type=float, default=1, help="관찰 대기 시간 배율 (기본 1)")
    parser.add_argument("--auth-timeout", type=int, default=180, help="GitHub 승인 대기 한도(초)")
    args = parser.parse_args()
    if not math.isfinite(args.pace) or args.pace < 0 or args.auth_timeout < 1:
        parser.error("pace는 0 이상, auth-timeout은 1 이상이어야 합니다.")
    demo = Demo(args)
    demo.preflight()
    if args.check:
        return
    demo.prepare()
    input("준비 완료. Android Studio에서 녹화를 시작한 다음 Enter: ")
    scenes = ["intro", "vehicle", "auth", "chat", "quest", "overlay"] if args.scene == "all" else [args.scene]
    for scene in scenes:
        getattr(demo, scene)()
    print("선택한 장면 실행이 끝났습니다. 녹화 저장은 Android Studio에서 진행하세요.")


if __name__ == "__main__":
    try:
        main()
    except (DemoError, subprocess.TimeoutExpired, ET.ParseError, EOFError) as error:
        print(f"중단: {error}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n사용자가 중단했습니다. 에뮬레이터의 주차/배터리 상태를 확인하세요.", file=sys.stderr)
        sys.exit(130)
