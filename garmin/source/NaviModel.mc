import Toybox.Attention;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

/**
 * NaviModel stores navigation state received from the Android companion app
 * and triggers haptic vibrations when maneuvers change.
 */
(:glance)
class NaviModel {

    public var action as String = "NONE";
    public var distance as String = "--";
    public var street as String = "";
    public var eta as String = "";
    public var remainDist as String = "";
    public var remainTime as String = "";
    public var exitNumber as Number = 0;
    public var hasRoute as Boolean = false;
    public var phoneBattery as Number = -1;   // Poziom baterii telefonu w % (-1 = brak danych)
    public var showClock as Boolean = false;  // Czy tarcza zegarka (godzina) jest aktywna

    private var _lastAction as String = "NONE";
    private var _lastDistance as String = "";

    function initialize() {
    }

    /**
     * Toggles clock / watch face screen overlay.
     */
    public function toggleClock() as Void {
        showClock = !showClock;
        WatchUi.requestUpdate();
    }

    /**
     * Updates internal state from a Dictionary transmitted via Connect IQ Mobile SDK.
     */
    function updateFromDictionary(dict as Lang.Dictionary) as Void {

        var newAction = dict.get("action");
        if (newAction != null && newAction instanceof Lang.String) {
            action = newAction;
        }

        var newDist = dict.get("distance");
        if (newDist != null && newDist instanceof Lang.String) {
            distance = newDist;
        }

        var newStreet = dict.get("street");
        if (newStreet != null && newStreet instanceof Lang.String) {
            street = newStreet;
        }

        var newEta = dict.get("eta");
        if (newEta != null && newEta instanceof Lang.String) {
            eta = newEta;
        }

        var newRemainDist = dict.get("remainDist");
        if (newRemainDist != null && newRemainDist instanceof Lang.String) {
            remainDist = newRemainDist;
        }

        var newRemainTime = dict.get("remainTime");
        if (newRemainTime != null && newRemainTime instanceof Lang.String) {
            remainTime = newRemainTime;
        }

        var newExitNum = dict.get("exitNumber");
        if (newExitNum != null && newExitNum instanceof Lang.Number) {
            exitNumber = newExitNum;
        }

        var newHasRoute = dict.get("hasRoute");
        if (newHasRoute != null && newHasRoute instanceof Lang.Boolean) {
            hasRoute = newHasRoute;
        }

        var newPhoneBattery = dict.get("phoneBattery");
        if (newPhoneBattery != null) {
            if (newPhoneBattery instanceof Lang.Number) {
                phoneBattery = newPhoneBattery as Lang.Number;
            } else if (newPhoneBattery has :toNumber) {
                phoneBattery = (newPhoneBattery as Object).toNumber();
            }
        }

        // Trigger haptic vibration if maneuver changed or active navigation just started
        if (hasRoute) {
            if (!action.equals(_lastAction) || (!distance.equals(_lastDistance) && isImminentTurn(distance))) {
                triggerHaptic();
            }
        }

        _lastAction = action;
        _lastDistance = distance;

        WatchUi.requestUpdate();
    }

    /**
     * Checks if distance indicates turn is imminent (< 100m)
     */
    private function isImminentTurn(distStr as String) as Boolean {
        var lower = distStr.toLower();
        if (lower.find("50 m") != null || lower.find("100 m") != null) {
            return true;
        }
        return false;
    }

    /**
     * Triggers distinct double-pulse vibration on Garmin vívoactive 4 (Disabled)
     */
    public function triggerHaptic() as Void {
        // Wibracje wyłączone na życzenie użytkownika
        return;
    }
}

