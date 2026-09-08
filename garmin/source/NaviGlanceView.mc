import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

/**
 * NaviGlanceView provides a concise summary widget in the vívoactive 4 glance list.
 */
(:glance)
class NaviGlanceView extends WatchUi.GlanceView {

    private var _model as NaviModel;

    function initialize(model as NaviModel) {
        GlanceView.initialize();
        _model = model;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var h = dc.getHeight();

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        // App title
        dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
        dc.drawText(0, 4, Graphics.FONT_TINY, "Maps Navi", Graphics.TEXT_JUSTIFY_LEFT);

        // Status line
        if (_model.hasRoute) {
            var summary = _model.distance + " • " + _model.street;
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(0, (h / 2) + 2, Graphics.FONT_XTINY, summary, Graphics.TEXT_JUSTIFY_LEFT);
        } else {
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(0, (h / 2) + 2, Graphics.FONT_XTINY, "Brak trasy", Graphics.TEXT_JUSTIFY_LEFT);
        }
    }
}

