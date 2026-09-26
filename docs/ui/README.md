# UI reference exports

These full-screen SVGs come from the
[v5 Figma page](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-2).
The conversation exports were refreshed from section 03 on 2026-09-26.
The index links each export to its source frame. They are visual references, not
Android assets or evidence that a feature is implemented. See [DESIGN.md](../DESIGN.md)
for behavior and [ARCHITECTURE.md](../ARCHITECTURE.md) for current support.

Every export has a 2560 × 1440 viewBox. Standard frames show system bars at y=0–96
and y=1280–1440, leaving 2560 × 1184 app content. The keyboard-input frame shows
a keyboard in place of the bottom bar. Compare app content using actual device insets.

Keep exports grouped by feature and named for their visible state. Replace a file
only with a new export from its linked frame. The connected screen includes a
[Lucide settings icon](https://github.com/lucide-icons/lucide/blob/0.468.0/icons/settings.svg)
([license](licenses/lucide.txt)).

## Screen index

### Shell

| Reference | Visible state |
| --- | --- |
| [home.svg](shell/home.svg) | [Home](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3973) |
| [menu.svg](shell/menu.svg) | [Home menu overlay](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3844) |
| [settings.svg](shell/settings.svg) | [Settings](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3641) |

### Connection

| Reference | Visible state |
| --- | --- |
| [introduction.svg](connection/introduction.svg) | [Connection introduction](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3458) |
| [qr-approval.svg](connection/qr-approval.svg) | [QR approval pending](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3361) |
| [qr-expired.svg](connection/qr-expired.svg) | [Approval expired](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3086) |
| [approval-help.svg](connection/approval-help.svg) | [Address and code help](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3167) |
| [connected.svg](connection/connected.svg) | [Connected](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3261) |
| [reconnect.svg](connection/reconnect.svg) | [Reconnect required](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3556) |
| [disconnect-confirmation.svg](connection/disconnect-confirmation.svg) | [Disconnect confirmation](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-2907) |
| [access-check.svg](connection/access-check.svg) | [Copilot access check](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-2989) |

### Conversation

| Reference | Visible state |
| --- | --- |
| [empty.svg](conversation/empty.svg) | [Conversation entry](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=630-2118) |
| [messages.svg](conversation/messages.svg) | [Conversation with messages](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=630-2006) |
| [reply-pending.svg](conversation/reply-pending.svg) | [Waiting for reply](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=630-1908) |
| [voice-listening.svg](conversation/voice-listening.svg) | [Voice recording](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=630-2206) |
| [voice-review.svg](conversation/voice-review.svg) | [Transcript review before send](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=630-2294) |
| [keyboard-input.svg](conversation/keyboard-input.svg) | [Keyboard and composer](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=630-2382) |
| [reply-failed.svg](conversation/reply-failed.svg) | [Reply failed with inline retry](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=630-1668) |
| [parking-required.svg](conversation/parking-required.svg) | [Parking required dialog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=676-823) |
| [network-error.svg](conversation/network-error.svg) | [Network error dialog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=676-700) |
| [network-checking.svg](conversation/network-checking.svg) | [Copilot connection recheck dialog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=676-1018) |

### Customization

| Reference | Visible state |
| --- | --- |
| [friend-preview.svg](customization/friend-preview.svg) | [Friend preview before apply](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-2094) |
| [accessories.svg](customization/accessories.svg) | [Accessories catalog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-1200) |
| [item-selected.svg](customization/item-selected.svg) | [Unowned accessory selected before purchase](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-1313) |
| [backgrounds.svg](customization/backgrounds.svg) | [Background catalog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-1868) |
| [purchase-pending.svg](customization/purchase-pending.svg) | [Purchase pending](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-1426) |
| [owned.svg](customization/owned.svg) | [Owned item before apply](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-1539) |
| [apply-pending.svg](customization/apply-pending.svg) | [Application pending](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-1652) |
| [applied.svg](customization/applied.svg) | [Item applied](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-1755) |
| [insufficient-points.svg](customization/insufficient-points.svg) | [Insufficient points](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-1981) |

### Vehicle

| Reference | Visible state |
| --- | --- |
| [charging-required.svg](vehicle/charging-required.svg) | [Charging required / hungry](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-890) |
| [tire-warning.svg](vehicle/tire-warning.svg) | [Tire warning / sick](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-999) |
| [checked-items-normal.svg](vehicle/checked-items-normal.svg) | [Checked items normal / default](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-1099) |

### Quests

| Reference | Visible state |
| --- | --- |
| [list-all.svg](quests/list-all.svg) | [All quests](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-83) |
| [list-in-progress.svg](quests/list-in-progress.svg) | [In-progress quests](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-329) |
| [list-completed.svg](quests/list-completed.svg) | [Completed quests](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-446) |
| [empty-in-progress.svg](quests/empty-in-progress.svg) | [No in-progress quests](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-721) |
| [empty-completed.svg](quests/empty-completed.svg) | [No completed quests](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-805) |
| [detail-actionable.svg](quests/detail-actionable.svg) | [Quest detail before completion](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-565) |
| [detail-claimable.svg](quests/detail-claimable.svg) | [Quest detail ready to claim](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-5) |
| [detail-completed.svg](quests/detail-completed.svg) | [Quest detail already rewarded](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-643) |
| [reward-success.svg](quests/reward-success.svg) | [Reward committed dialog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-202) |
