import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

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
        // --- TOP SECTION: ETA and Remaining Distance ---
        var topText = "";
        if (_model.eta.length() > 0 && _model.remainDist.length() > 0) {
            topText = _model.eta + "  •  " + _model.remainDist;
        } else if (_model.eta.length() > 0) {
            topText = "ETA " + _model.eta;
        } else if (_model.remainDist.length() > 0) {
            topText = _model.remainDist;
        }

        if (topText.length() > 0) {
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, 16, Graphics.FONT_XTINY, topText, Graphics.TEXT_JUSTIFY_CENTER);
        }

        // Top subtle divider line
        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(1);
        dc.drawLine(cx - 50, 38, cx + 50, 38);

        // --- CENTER SECTION: Vector Maneuver Graphic ---
        // Maneuver icon centered at cx=130, cy=95 with half-size 35 px
        ArrowRenderer.drawManeuver(dc, cx, 95, 35, _model.action, _model.exitNumber);

        // --- MIDDLE-LOWER SECTION: Turn Distance ---
        var distColor = Graphics.COLOR_WHITE;
        var lowerDist = _model.distance.toLower();
        if (lowerDist.find("50 m") != null || lowerDist.find("100 m") != null) {
            distColor = Graphics.COLOR_YELLOW; // Warning highlight when close to turn
        }

        dc.setColor(distColor, Graphics.COLOR_TRANSPARENT);
        // Distance in bold large font
        dc.drawText(cx, 142, Graphics.FONT_LARGE, _model.distance, Graphics.TEXT_JUSTIFY_CENTER);

        // --- BOTTOM SECTION: Target Street Name ---
        if (_model.street.length() > 0) {
            var streetStr = fitTextToWidth(dc, _model.street, Graphics.FONT_SMALL, 190);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, 188, Graphics.FONT_SMALL, streetStr, Graphics.TEXT_JUSTIFY_CENTER);
        }

        // --- BOTTOM FOOTER: Remaining duration ---
        if (_model.remainTime.length() > 0) {
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, 218, Graphics.FONT_XTINY, _model.remainTime, Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    /**
     * Renders clean standby screen when no route is active in Google Maps.
     */
    private function renderStandby(
        dc as Graphics.Dc,
        cx as Number,
        cy as Number,
        w as Number,
        h as Number
    ) as Void {
        // App title header
        dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, 24, Graphics.FONT_TINY, "MAPS NAVI", Graphics.TEXT_JUSTIFY_CENTER);

        // Stylized compass icon
        ArrowRenderer.drawManeuver(dc, cx, 110, 44, "NONE", 0);

        // Status text
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, 172, Graphics.FONT_SMALL, "Oczekiwanie na trasę...", Graphics.TEXT_JUSTIFY_CENTER);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, 204, Graphics.FONT_XTINY, "Włącz nawigację w Google Maps", Graphics.TEXT_JUSTIFY_CENTER);
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

