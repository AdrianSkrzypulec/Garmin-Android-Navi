import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Sensor;
import Toybox.SensorHistory;
import Toybox.System;
import Toybox.Time;
import Toybox.Time.Gregorian;
import Toybox.WatchUi;
import Toybox.Weather;

/**
 * NaviView renders the full-screen navigation UI on the 260x260 Garmin vívoactive 4 screen.
 */
class NaviView extends WatchUi.View {

    private var _model as NaviModel;

    function initialize(model as NaviModel) {
        View.initialize();
        _model = model;
    }

    function onLayout(dc as Graphics.Dc) as Void {
    }

    function onShow() as Void {
    }

    function onHide() as Void {
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var cy = h / 2;

        // 1. Clear background to deep black for maximum MIP contrast
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        if (_model.hasRoute) {
            renderActiveRoute(dc, cx, cy, w, h);
        } else {
            renderStandby(dc, cx, cy, w, h);
        }
    }

    /**
     * Renders active turn-by-turn navigation dashboard.
     */
    private function renderActiveRoute(
        dc as Graphics.Dc,
        cx as Number,
        cy as Number,
        w as Number,
        h as Number
    ) as Void {
        // --- TOP SECTION: ETA & Distance OR Current Time & Temperature ---
        var topText = getTopHeaderText();

        if (topText.length() > 0) {
            var headerColor = _model.showClock ? Graphics.COLOR_WHITE : Graphics.COLOR_LT_GRAY;
            dc.setColor(headerColor, Graphics.COLOR_TRANSPARENT);
            // 50% larger font (FONT_TINY) for maximum top header readability
            dc.drawText(cx, 10, Graphics.FONT_TINY, topText, Graphics.TEXT_JUSTIFY_CENTER);
        }

        // Top subtle divider line
        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(1);
        dc.drawLine(cx - 55, 38, cx + 55, 38);

        // --- CENTER SECTION: Vector Maneuver Graphic ---
        // Maneuver icon centered at cx=130, cy=92 with size 38 px (fits cleanly below divider)
        ArrowRenderer.drawManeuver(dc, cx, 92, 38, _model.action, _model.exitNumber);

        // --- MIDDLE-LOWER SECTION: Turn Distance ---
        var distColor = Graphics.COLOR_WHITE;
        var lowerDist = _model.distance.toLower();
        if (lowerDist.find("50 m") != null || lowerDist.find("100 m") != null) {
            distColor = Graphics.COLOR_YELLOW; // Warning highlight when close to turn
        }

        dc.setColor(distColor, Graphics.COLOR_TRANSPARENT);
        // Distance in bold large font
        dc.drawText(cx, 138, Graphics.FONT_LARGE, _model.distance, Graphics.TEXT_JUSTIFY_CENTER);

        // --- BOTTOM SECTION: Target Street Name ---
        if (_model.street.length() > 0) {
            var streetStr = fitTextToWidth(dc, _model.street, Graphics.FONT_SMALL, 190);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, 176, Graphics.FONT_SMALL, streetStr, Graphics.TEXT_JUSTIFY_CENTER);
        }

        // --- BOTTOM FOOTER: Remaining duration ---
        if (_model.remainTime.length() > 0) {
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, 204, Graphics.FONT_XTINY, _model.remainTime, Graphics.TEXT_JUSTIFY_CENTER);
        }

        // --- BOTTOM BAR: Phone Battery ---
        renderBottomBar(dc, cx, h, w);
    }

    /**
     * Builds top header string: either ETA & Remaining Distance OR Current Time & Temperature (toggled via top button).
     */
    private function getTopHeaderText() as String {
        if (_model.showClock) {
            // Mode B (Top button active): Current Time & Temperature
            var clockTime = System.getClockTime();
            var hours = clockTime.hour;
            var mins = clockTime.min;
            var timeStr = Lang.format("$1$:$2$", [hours.format("%02d"), mins.format("%02d")]);
            var tempStr = getFormattedTemperature();
            return timeStr + "  •  " + tempStr;
        } else {
            // Mode A (Default): ETA & Remaining Distance
            if (_model.eta.length() > 0 && _model.remainDist.length() > 0) {
                return _model.eta + "  •  " + _model.remainDist;
            } else if (_model.eta.length() > 0) {
                return "ETA " + _model.eta;
            } else if (_model.remainDist.length() > 0) {
                return _model.remainDist;
            }
            return "";
        }
    }

    /**
     * Retrieves current temperature string in °C from Weather, SensorHistory, or Sensor API.
     */
    private function getFormattedTemperature() as String {
        // 1. Try Weather API
        if (Toybox has :Weather) {
            try {
                var cond = Weather.getCurrentConditions();
                if (cond != null && cond.temperature != null) {
                    return cond.temperature.toString() + "°C";
                }
            } catch (e) {
            }
        }

        // 2. Try SensorHistory API
        if (Toybox has :SensorHistory && SensorHistory has :getTemperatureHistory) {
            try {
                var iter = SensorHistory.getTemperatureHistory({:period => 1});
                if (iter != null) {
                    var sample = iter.next();
                    if (sample != null && sample.data != null) {
                        var val = sample.data.toNumber();
                        return val.toString() + "°C";
                    }
                }
            } catch (e) {
            }
        }

        // 3. Try Sensor Info
        if (Toybox has :Sensor) {
            try {
                var info = Sensor.getInfo();
                if (info has :temperature && info.temperature != null) {
                    return info.temperature.toString() + "°C";
                }
            } catch (e) {
            }
        }

        return "--°C";
    }

    /**
     * Renders clean standby screen when no route is active.
     */
    private function renderStandby(
        dc as Graphics.Dc,
        cx as Number,
        cy as Number,
        w as Number,
        h as Number
    ) as Void {
        // Top header: If top button pressed, show Time & Temp; otherwise show "MAPS NAVI"
        if (_model.showClock) {
            var clockTime = System.getClockTime();
            var timeStr = Lang.format("$1$:$2$", [clockTime.hour.format("%02d"), clockTime.min.format("%02d")]);
            var header = timeStr + "  •  " + getFormattedTemperature();
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, 10, Graphics.FONT_TINY, header, Graphics.TEXT_JUSTIFY_CENTER);
        } else {
            dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, 10, Graphics.FONT_TINY, "MAPS NAVI", Graphics.TEXT_JUSTIFY_CENTER);
        }

        // Stylized compass icon
        ArrowRenderer.drawManeuver(dc, cx, 110, 44, "NONE", 0);

        // Status text
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, 172, Graphics.FONT_SMALL, "Oczekiwanie na trasę...", Graphics.TEXT_JUSTIFY_CENTER);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, 200, Graphics.FONT_XTINY, "Włącz nawigację w telefonie", Graphics.TEXT_JUSTIFY_CENTER);

        // --- BOTTOM BAR: Phone Battery (standby) ---
        renderBottomBar(dc, cx, h, w);
    }

    /**
     * Renders phone battery percentage centered at the bottom of the round display.
     */
    private function renderBottomBar(
        dc as Graphics.Dc,
        cx as Number,
        h as Number,
        w as Number
    ) as Void {
        if (_model.phoneBattery < 0) {
            return;
        }

        var bat = _model.phoneBattery;

        // Colour: green > 50%, yellow 21-50%, red <= 20%
        var batColor = Graphics.COLOR_GREEN;
        if (bat <= 20) {
            batColor = Graphics.COLOR_RED;
        } else if (bat <= 50) {
            batColor = Graphics.COLOR_YELLOW;
        } else {
            batColor = Graphics.COLOR_GREEN;
        }

        var txt = bat.toString() + "%";
        var batY = h - 28; // Y=232 for 260px display (50% larger FONT_TINY)

        dc.setColor(batColor, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, batY, Graphics.FONT_TINY, txt, Graphics.TEXT_JUSTIFY_CENTER);
    }

    /**
     * Truncates text with ellipsis if it exceeds the maximum pixel width.
     */
    private function fitTextToWidth(
        dc as Graphics.Dc,
        text as String,
        font as Graphics.FontDefinition,
        maxWidth as Number
    ) as String {
        var strWidth = dc.getTextWidthInPixels(text, font);
        if (strWidth <= maxWidth) {
            return text;
        }

        var result = text;
        while (result.length() > 3 && dc.getTextWidthInPixels(result + "...", font) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }
}
