import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

/**
 * NaviApp manages the Connect IQ application lifecycle and Bluetooth
 * message registration with the Android companion app.
 */
(:glance)
class NaviApp extends Application.AppBase {

    private var _model as NaviModel;
    private var _view as NaviView?;
    private var _delegate as NaviDelegate?;
    private var _glanceView as NaviGlanceView?;

    function initialize() {
        AppBase.initialize();
        _model = new NaviModel();
    }

    function onStart(state as Lang.Dictionary?) as Void {
        // Register for incoming turn-by-turn messages from Android Connect IQ SDK
        if (Communications has :registerForPhoneAppMessages) {
            try {
                Communications.registerForPhoneAppMessages(method(:onPhoneAppMessage));
                System.println("NaviApp: Registered for phone messages.");
            } catch (e) {
                System.println("NaviApp: Failed to register phone listener: " + e.getErrorMessage());
            }
        }
    }

    function onStop(state as Lang.Dictionary?) as Void {
    }

    /**
     * Callback receiving navigation updates from Android GarminBridge.
     */
    function onPhoneAppMessage(msg as Communications.PhoneAppMessage) as Void {
        var payload = msg.data;
        if (payload != null && payload instanceof Lang.Dictionary) {
            System.println("NaviApp: Received message -> " + payload.toString());
            _model.updateFromDictionary(payload as Lang.Dictionary);
        }
    }

    /**
     * Returns the main full-screen navigation view.
     */
    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        _view = new NaviView(_model);
        _delegate = new NaviDelegate(_model);
        return [_view, _delegate];
    }

    /**
     * Returns the widget glance view for vívoactive 4 widget list.
     */
    function getGlanceView() as [WatchUi.GlanceView] or [WatchUi.GlanceView, WatchUi.GlanceViewDelegate] or Null {
        _glanceView = new NaviGlanceView(_model);
        return [_glanceView];
    }
}

(:glance)
function getApp() as NaviApp {
    return Application.getApp() as NaviApp;
}

