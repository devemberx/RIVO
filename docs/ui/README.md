# UI reference exports

These SVGs link to their source frames in Figma. Store screens use
[revised v8](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=935-2543).
Vehicle screens and the three notification states below use
[revised v6](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=855-1393);
the other references use [v5](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-2).
See [DESIGN.md](../DESIGN.md) for behavior and [ARCHITECTURE.md](../ARCHITECTURE.md)
for implementation status.

The connected screen includes a
[Lucide settings icon](https://github.com/lucide-icons/lucide/blob/0.468.0/icons/settings.svg)
([license](licenses/lucide.txt)).

## Screen index

### Shell

| Reference | Visible state |
| --- | --- |
| [home.svg](shell/home.svg) | [Home with notification indicator; three alerts](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3973) |
| [home-no-notifications.svg](shell/home-no-notifications.svg) | [Home without notification indicator](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=777-713) |
| [home-many-notifications.svg](shell/home-many-notifications.svg) | [Home with notification indicator; five alerts](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=777-815) |
| [menu.svg](shell/menu.svg) | [Menu with notification row; three alerts](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=524-3844) |
| [menu-no-notifications.svg](shell/menu-no-notifications.svg) | [Menu without notification count badge](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=783-1025) |
| [menu-many-notifications.svg](shell/menu-many-notifications.svg) | [Menu with notification row; five alerts](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=783-1164) |
| [notifications-empty.svg](shell/notifications-empty.svg) | [Compact header with raised bell empty state · v6](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=855-7910) |
| [notifications-three.svg](shell/notifications-three.svg) | [Alerts and reward · v6](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=855-8014) |
| [notifications-many.svg](shell/notifications-many.svg) | [Scrollable alerts · v6](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=855-7763) |
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

### Store (v8)

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

### Earlier customization references (v5)

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
| [charging-required.svg](vehicle/charging-required.svg) | [Low battery · v6](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=855-9562) |
| [tire-warning.svg](vehicle/tire-warning.svg) | [Low tire pressure · v6](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=855-2734) |
| [checked-items-normal.svg](vehicle/checked-items-normal.svg) | [Normal · v6](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=855-2873) |
| [card-selection.svg](vehicle/card-selection.svg) | [Card selection · v6](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=855-3173) |
| [parking-required.svg](vehicle/parking-required.svg) | [Parking required · v6](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=855-3475) |
| [card-catalog.svg](vehicle/card-catalog.svg) | [30 cards and VSS signals · v6](https://www.figma.com/design/7tyb4oJsJAUc15KnU7H0F6?node-id=855-3017) |

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
