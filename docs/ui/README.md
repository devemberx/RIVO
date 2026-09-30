# UI reference exports

These SVGs come from the [current v8 Figma page](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-465).
Each export links to its source frame.
See [DESIGN.md](../DESIGN.md) for behavior and [ARCHITECTURE.md](../ARCHITECTURE.md)
for implementation status.

The connected screen includes a
[Lucide settings icon](https://github.com/lucide-icons/lucide/blob/0.468.0/icons/settings.svg)
([license](licenses/lucide.txt)).

## Screen index

### Shell

| Reference | Visible state |
| --- | --- |
| [home.svg](shell/home.svg) | [Home with notification indicator; three alerts](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-7005) |
| [home-no-notifications.svg](shell/home-no-notifications.svg) | [Home without notification indicator](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-7173) |
| [home-many-notifications.svg](shell/home-many-notifications.svg) | [Home with notification indicator; five alerts](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-7089) |
| [menu.svg](shell/menu.svg) | [Menu with notification row; three alerts](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-6603) |
| [menu-no-notifications.svg](shell/menu-no-notifications.svg) | [Menu without notification count badge](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-6873) |
| [menu-many-notifications.svg](shell/menu-many-notifications.svg) | [Menu with notification row; five alerts](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-6738) |
| [notifications-empty.svg](shell/notifications-empty.svg) | [Compact header with raised bell empty state](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-7385) |
| [notifications-three.svg](shell/notifications-three.svg) | [Alerts and reward](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-7489) |
| [notifications-many.svg](shell/notifications-many.svg) | [Scrollable alerts](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-7255) |
| [settings.svg](shell/settings.svg) | [Settings](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-6402) |

### Connection

| Reference | Visible state |
| --- | --- |
| [introduction.svg](connection/introduction.svg) | [Connection introduction](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-6219) |
| [qr-approval.svg](connection/qr-approval.svg) | [QR approval pending](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-6122) |
| [qr-expired.svg](connection/qr-expired.svg) | [Approval expired](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-5845) |
| [approval-help.svg](connection/approval-help.svg) | [Address and code help](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-5926) |
| [connected.svg](connection/connected.svg) | [Connected](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-6020) |
| [reconnect.svg](connection/reconnect.svg) | [Reconnect required](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-6317) |
| [disconnect-confirmation.svg](connection/disconnect-confirmation.svg) | [Disconnect confirmation](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-5666) |
| [access-check.svg](connection/access-check.svg) | [Copilot access check](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-5748) |

### Conversation

| Reference | Visible state |
| --- | --- |
| [empty.svg](conversation/empty.svg) | [Conversation entry](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-5252) |
| [messages.svg](conversation/messages.svg) | [Conversation with messages](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-5156) |
| [reply-pending.svg](conversation/reply-pending.svg) | [Waiting for reply](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-5052) |
| [voice-listening.svg](conversation/voice-listening.svg) | [Voice recording](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-4780) |
| [voice-review.svg](conversation/voice-review.svg) | [Transcript review before send](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-4684) |
| [keyboard-input.svg](conversation/keyboard-input.svg) | [Keyboard and composer](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-4588) |
| [reply-failed.svg](conversation/reply-failed.svg) | [Reply failed with inline retry](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-4476) |
| [parking-required.svg](conversation/parking-required.svg) | [Parking required dialog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-5560) |
| [network-error.svg](conversation/network-error.svg) | [Network error dialog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-5343) |
| [network-checking.svg](conversation/network-checking.svg) | [Copilot connection recheck dialog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-5453) |

### Store

| Reference | Visible state |
| --- | --- |
| [friends-catalog.svg](store/friends-catalog.svg) | [Three friends in the catalog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-2544) |
| [clothes-catalog.svg](store/clothes-catalog.svg) | [Clothing catalog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-2746) |
| [clothes-owned-filter.svg](store/clothes-owned-filter.svg) | [Clothing with the owned-only filter selected](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-2860) |
| [backgrounds-catalog.svg](store/backgrounds-catalog.svg) | [Background catalog with Lake Park before Golden City](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-2962) |
| [background-golden-city.svg](store/background-golden-city.svg) | [Golden City selected in the background catalog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-3098) |
| [effects-catalog.svg](store/effects-catalog.svg) | [Effects catalog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-3234) |
| [props-catalog.svg](store/props-catalog.svg) | [Props catalog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-3415) |
| [parking-required.svg](store/parking-required.svg) | [Store parking interruption](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-3529) |
| [insufficient-points.svg](store/insufficient-points.svg) | [Insufficient points](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-3722) |
| [friend-ras.svg](store/friend-ras.svg) | [Ras friend preview](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-3836) |
| [clothes-mobi.svg](store/clothes-mobi.svg) | [Mobi clothing](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-3938) |
| [clothes-ras.svg](store/clothes-ras.svg) | [Ras clothing](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-4052) |
| [purchase-confirmation.svg](store/purchase-confirmation.svg) | [Ras purchase confirmation](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-4155) |
| [purchase-complete.svg](store/purchase-complete.svg) | [Ras purchase complete](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-4270) |
| [ras-active.svg](store/ras-active.svg) | [Ras as the active friend](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-4372) |

### Vehicle

| Reference | Visible state |
| --- | --- |
| [charging-required.svg](vehicle/charging-required.svg) | [Low battery](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-2432) |
| [tire-warning.svg](vehicle/tire-warning.svg) | [Low tire pressure](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-1584) |
| [checked-items-normal.svg](vehicle/checked-items-normal.svg) | [Normal](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-1693) |
| [card-selection.svg](vehicle/card-selection.svg) | [Card selection](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-2112) |
| [parking-required.svg](vehicle/parking-required.svg) | [Parking required](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-2309) |
| [card-catalog.svg](vehicle/card-catalog.svg) | [30 cards and VSS signals](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-1803) |

### Quests

| Reference | Visible state |
| --- | --- |
| [list-all.svg](quests/list-all.svg) | [All quests](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-617) |
| [list-in-progress.svg](quests/list-in-progress.svg) | [In-progress quests](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-869) |
| [list-completed.svg](quests/list-completed.svg) | [Completed quests](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-986) |
| [empty-in-progress.svg](quests/empty-in-progress.svg) | [No in-progress quests](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-1261) |
| [empty-completed.svg](quests/empty-completed.svg) | [No completed quests](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-1355) |
| [detail-actionable.svg](quests/detail-actionable.svg) | [Quest detail before completion](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-1105) |
| [detail-claimable.svg](quests/detail-claimable.svg) | [Quest detail ready to claim](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-539) |
| [detail-completed.svg](quests/detail-completed.svg) | [Quest detail already rewarded](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-1183) |
| [reward-success.svg](quests/reward-success.svg) | [Reward committed dialog](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-737) |
| [parking-required.svg](quests/parking-required.svg) | [Quest parking interruption](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-1449) |
