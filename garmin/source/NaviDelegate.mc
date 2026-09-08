import Toybox.Lang;
import Toybox.WatchUi;

/**
 * NaviDelegate handles touchscreen taps and physical button clicks on Garmin vívoactive 4.
 */
class NaviDelegate extends WatchUi.BehaviorDelegate {

    private var _model as NaviModel;

    function initialize(model as NaviModel) {
        BehaviorDelegate.initialize();
        _model = model;
    }

    /**
     * Tapping the screen forces UI redraw.
     */
    function onTap(clickEvent as WatchUi.ClickEvent) as Lang.Boolean {
        WatchUi.requestUpdate();
        return true;
    }

    /**
     * Handle physical back button (vívoactive 4 bottom-right button)
     */
    function onBack() as Lang.Boolean {
        // Allow default behavior to exit app/widget
        return false;
    }

    /**
     * Handle physical select button (vívoactive 4 top-right button)
     */
    function onSelect() as Lang.Boolean {
        return true;
    }
}
