import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

/**
 * NaviDelegate handles physical button clicks on Garmin vívoactive 4.
 * Touchscreen interactions are completely disabled.
 */
class NaviDelegate extends WatchUi.BehaviorDelegate {

    private var _model as NaviModel;

    function initialize(model as NaviModel) {
        BehaviorDelegate.initialize();
        _model = model;
    }

    /**
     * Intercepts physical key presses exclusively.
     * Top button (KEY_ENTER / KEY_START) -> Toggles clock.
     * Bottom button (KEY_ESC / KEY_LAP) -> Exits application completely.
     */
    function onKey(keyEvent as WatchUi.KeyEvent) as Lang.Boolean {
        var key = keyEvent.getKey();

        if (key == WatchUi.KEY_ENTER || key == WatchUi.KEY_START) {
            _model.toggleClock();
            return true;
        }

        if (key == WatchUi.KEY_ESC || key == WatchUi.KEY_LAP) {
            System.exit();
        }

        return true;
    }

    /**
     * Touchscreen interactions completely disabled (consume all events).
     */
    function onTap(clickEvent as WatchUi.ClickEvent) as Lang.Boolean {
        return true;
    }

    function onClick(clickEvent as WatchUi.ClickEvent) as Lang.Boolean {
        return true;
    }

    function onSwipe(swipeEvent as WatchUi.SwipeEvent) as Lang.Boolean {
        return true;
    }

    function onHold(clickEvent as WatchUi.ClickEvent) as Lang.Boolean {
        return true;
    }

    function onRelease(clickEvent as WatchUi.ClickEvent) as Lang.Boolean {
        return true;
    }

    /**
     * Prevent default touchscreen tap -> onSelect behavior.
     */
    function onSelect() as Lang.Boolean {
        return true;
    }

    /**
     * Physical back button fallback.
     */
    function onBack() as Lang.Boolean {
        System.exit();
    }
}
