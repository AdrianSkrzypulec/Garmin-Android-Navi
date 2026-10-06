import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;

/**
 * ArrowRenderer renders high-contrast, sunlight-readable vector maneuver graphics
 * optimized for the Garmin vívoactive 4 (260x260 px MIP display).
 */
class ArrowRenderer {

    // Primary palette colors
    private static const COLOR_MAIN = Graphics.COLOR_GREEN;
    private static const COLOR_ACCENT = Graphics.COLOR_YELLOW;
    private static const COLOR_INACTIVE = Graphics.COLOR_DK_GRAY;
    private static const COLOR_WHITE = Graphics.COLOR_WHITE;
    private static const COLOR_BLACK = Graphics.COLOR_BLACK;

    /**
     * Main dispatch method to render the vector maneuver icon.
     * @param dc Draw context
     * @param cx Center X coordinate
     * @param cy Center Y coordinate
     * @param size Radius/half-size bounding box (e.g. 36-40 px)
     * @param action Maneuver string identifier
     * @param exitNumber Roundabout exit index (1-based)
     */
    static function drawManeuver(
        dc as Graphics.Dc,
        cx as Number,
        cy as Number,
        size as Number,
        action as String,
        exitNumber as Number
    ) as Void {
        if (action.equals("STRAIGHT")) {
            drawStraight(dc, cx, cy, size);
        } else if (action.equals("TURN_LEFT")) {
            drawTurn(dc, cx, cy, size, -1);
        } else if (action.equals("TURN_RIGHT")) {
            drawTurn(dc, cx, cy, size, 1);
        } else if (action.equals("SLIGHT_LEFT")) {
            drawSlightTurn(dc, cx, cy, size, -1);
        } else if (action.equals("SLIGHT_RIGHT")) {
            drawSlightTurn(dc, cx, cy, size, 1);
        } else if (action.equals("SHARP_LEFT")) {
            drawSharpTurn(dc, cx, cy, size, -1);
        } else if (action.equals("SHARP_RIGHT")) {
            drawSharpTurn(dc, cx, cy, size, 1);
        } else if (action.equals("U_TURN")) {
            drawUTurn(dc, cx, cy, size);
        } else if (action.equals("ROUNDABOUT")) {
            drawRoundabout(dc, cx, cy, size, exitNumber);
        } else if (action.equals("KEEP_LEFT")) {
            drawKeep(dc, cx, cy, size, -1);
        } else if (action.equals("KEEP_RIGHT")) {
            drawKeep(dc, cx, cy, size, 1);
        } else if (action.equals("DESTINATION")) {
            drawDestination(dc, cx, cy, size);
        } else {
            drawStandby(dc, cx, cy, size);
        }
    }

    // --- STRAIGHT ---
    private static function drawStraight(dc as Graphics.Dc, cx as Number, cy as Number, size as Number) as Void {
        var penW = 10;
        dc.setPenWidth(penW);
        dc.setColor(COLOR_MAIN, Graphics.COLOR_TRANSPARENT);

        var topY = cy - size + 14;
        var botY = cy + size - 4;
        dc.drawLine(cx, botY, cx, topY);

        // Arrow head pointing UP
        var headLen = 18;
        var headWidth = 16;
        var pts = [
            [cx, cy - size],
            [cx - headWidth, cy - size + headLen],
            [cx - 4, cy - size + headLen - 3],
            [cx + 4, cy - size + headLen - 3],
            [cx + headWidth, cy - size + headLen]
        ];
        dc.fillPolygon(pts);
    }

    // --- TURN LEFT (-1) / RIGHT (+1) ---
    private static function drawTurn(dc as Graphics.Dc, cx as Number, cy as Number, size as Number, dir as Number) as Void {
        var penW = 10;
        dc.setPenWidth(penW);
        dc.setColor(COLOR_MAIN, Graphics.COLOR_TRANSPARENT);

        var startX = cx - (dir * 16);
        var startY = cy + size - 4;
        var cornerX = startX;
        var cornerY = cy - 2;
        var endX = cx + (dir * (size - 14));
        var endY = cornerY;

        // Vertical stem
        dc.drawLine(startX, startY, cornerX, cornerY);
        // Horizontal turn
        dc.drawLine(cornerX, cornerY, endX, endY);

        // Arrow head pointing left or right
        var headLen = 18;
        var headW = 14;
        var tipX = cx + (dir * size);
        var tipY = endY;

        var pts = [
            [tipX, tipY],
            [tipX - (dir * headLen), tipY - headW],
            [tipX - (dir * (headLen - 4)), tipY],
            [tipX - (dir * headLen), tipY + headW]
        ];
        dc.fillPolygon(pts);
    }

    // --- SLIGHT LEFT (-1) / RIGHT (+1) ---
    private static function drawSlightTurn(dc as Graphics.Dc, cx as Number, cy as Number, size as Number, dir as Number) as Void {
        var penW = 10;
        dc.setPenWidth(penW);
        dc.setColor(COLOR_MAIN, Graphics.COLOR_TRANSPARENT);

        var startX = cx - (dir * 14);
        var startY = cy + size - 4;
        var bendX = cx - (dir * 8);
        var bendY = cy + 6;
        var endX = cx + (dir * (size - 12));
        var endY = cy - size + 14;

        dc.drawLine(startX, startY, bendX, bendY);
        dc.drawLine(bendX, bendY, endX, endY);

        // Angled arrow head (~45 degrees)
        var tipX = cx + (dir * size);
        var tipY = cy - size;
        var dx = tipX - bendX;
        var dy = tipY - bendY;
        var len = Math.sqrt(dx * dx + dy * dy).toFloat();
        if (len == 0) { len = 1.0; }
        var ux = dx / len;
        var uy = dy / len;
        var perpX = -uy;
        var perpY = ux;

        var hLen = 18.0;
        var hW = 12.0;

        var p1 = [tipX, tipY];
        var p2 = [(tipX - ux * hLen + perpX * hW).toNumber(), (tipY - uy * hLen + perpY * hW).toNumber()];
        var p3 = [(tipX - ux * (hLen - 4)).toNumber(), (tipY - uy * (hLen - 4)).toNumber()];
        var p4 = [(tipX - ux * hLen - perpX * hW).toNumber(), (tipY - uy * hLen - perpY * hW).toNumber()];

        dc.fillPolygon([p1, p2, p3, p4]);
    }

    // --- SHARP LEFT (-1) / RIGHT (+1) ---
    private static function drawSharpTurn(dc as Graphics.Dc, cx as Number, cy as Number, size as Number, dir as Number) as Void {
        var penW = 9;
        dc.setPenWidth(penW);
        dc.setColor(COLOR_MAIN, Graphics.COLOR_TRANSPARENT);

        var startX = cx - (dir * 18);
        var startY = cy + size - 6;
        var apexX = cx - (dir * 4);
        var apexY = cy - size + 16;
        var endX = cx + (dir * 18);
        var endY = cy + 8;

        dc.drawLine(startX, startY, apexX, apexY);
        dc.drawLine(apexX, apexY, endX, endY);

        // Arrow head pointing backward/downwards
        var tipX = cx + (dir * 24);
        var tipY = cy + 18;
        var pts = [
            [tipX, tipY],
            [tipX - (dir * 16), tipY - 14],
            [tipX - (dir * 4), tipY - 4],
            [tipX + (dir * 4), tipY - 14]
        ];
        dc.fillPolygon(pts);
    }

    // --- U-TURN ---
    private static function drawUTurn(dc as Graphics.Dc, cx as Number, cy as Number, size as Number) as Void {
        var penW = 9;
        dc.setPenWidth(penW);
        dc.setColor(COLOR_MAIN, Graphics.COLOR_TRANSPARENT);

        var rightX = cx + 16;
        var leftX = cx - 16;
        var botY = cy + size - 4;
        var loopY = cy - size + 20;

        // Upward right track
        dc.drawLine(rightX, botY, rightX, loopY);

        // Top arc loop
        var arcR = 16;
        dc.drawArc(cx, loopY, arcR, Graphics.ARC_CLOCKWISE, 0, 180);

        // Downward left track
        var leftArrowStop = cy + size - 20;
        dc.drawLine(leftX, loopY, leftX, leftArrowStop);

        // Downward arrow head
        var tipY = cy + size - 2;
        var pts = [
            [leftX, tipY],
            [leftX - 13, tipY - 16],
            [leftX, tipY - 10],
            [leftX + 13, tipY - 16]
        ];
        dc.fillPolygon(pts);
    }

    // --- ROUNDABOUT ---
    private static function drawRoundabout(
        dc as Graphics.Dc,
        cx as Number,
        cy as Number,
        size as Number,
        exitNum as Number
    ) as Void {
        // Normalize exit number: default to 2 (straight through) if missing
        var target = (exitNum > 0) ? exitNum : 2;

        // Number of exits to draw. Support 1–6. Minimum 3 for a useful roundabout.
        var numExits = (target > 4) ? target : 4;
        if (numExits < 3) { numExits = 3; }

        // Ring geometry
        var ringR = size - 8;
        var innerR = (ringR * 0.45).toNumber();   // hollow center size
        var exitLen = 14;                          // spoke extension beyond ring

        // ---- APPROACH ARROW from bottom (entry lane) ----
        // Entry at angle 270° (bottom = -90° from top = 3π/2)
        // We draw entry BEFORE ring so ring paints over overlap
        var entryAngle = Math.PI * 1.5;  // 270° = bottom
        var entryX = cx + (Math.cos(entryAngle) * ringR).toNumber();
        var entryY = cy - (Math.sin(entryAngle) * ringR).toNumber();
        var entryEndX = cx + (Math.cos(entryAngle) * (ringR + exitLen + 4)).toNumber();
        var entryEndY = cy - (Math.sin(entryAngle) * (ringR + exitLen + 4)).toNumber();

        dc.setPenWidth(7);
        dc.setColor(COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawLine(entryX, entryY, entryEndX, entryEndY);

        // Small arrowhead pointing INTO the circle (up)
        var eaLen = 10;
        var eaW = 7;
        dc.fillPolygon([
            [entryX, entryY],
            [entryX - eaW, entryY + eaLen],
            [entryX, entryY + (eaLen - 3)],
            [entryX + eaW, entryY + eaLen]
        ]);

        // ---- RING ----
        dc.setPenWidth(5);
        dc.setColor(COLOR_MAIN, Graphics.COLOR_TRANSPARENT);
        dc.drawCircle(cx, cy, ringR);

        // ---- EXIT SPOKES ----
        // Exits are numbered clockwise starting from the first exit after entry.
        // Entry is at bottom (270°). First exit is at ~0° (right), going clockwise.
        // angleStep distributes exits evenly around the circle, skipping the entry sector.
        //
        // We reserve ~60° for the entry gap and distribute exits in the remaining 300°.
        // Exit 1 starts at 0° (right), going clockwise (decreasing angle in standard math).
        //
        // In screen coords: angle 0°=right, increases counter-clockwise in math,
        // but drawArc/cos/sin use standard math angles.
        // Clockwise on screen means decreasing angle.
        //
        // Entry gap: bottom (270°) ±30° = 240°..300° blocked
        // Available arc: 300°..240° going clockwise (i.e. 300° down to -60°+360°=300° through 0°,90°,180°,240°)
        // Spread numExits exits evenly in that 300° arc.

        var gapDeg = 60.0;                        // degrees blocked by entry lane
        var arcDeg = 360.0 - gapDeg;              // available arc for exits = 300°
        var startDeg = 270.0 + (gapDeg / 2.0);   // first exit angle (clockwise from entry) = 300°
        var stepDeg = arcDeg / numExits.toFloat();

        for (var i = 0; i < numExits; i++) {
            // Clockwise on screen → subtract from startDeg
            var angleDeg = startDeg - (i.toFloat() * stepDeg);
            var angleRad = angleDeg * Math.PI / 180.0;

            // Spoke start (on ring) and end (beyond ring)
            var spokeStartX = cx + (Math.cos(angleRad) * ringR).toNumber();
            var spokeStartY = cy - (Math.sin(angleRad) * ringR).toNumber();
            var spokeEndX   = cx + (Math.cos(angleRad) * (ringR + exitLen)).toNumber();
            var spokeEndY   = cy - (Math.sin(angleRad) * (ringR + exitLen)).toNumber();

            var isTarget = (i + 1 == target);

            if (isTarget) {
                // Highlighted exit: thick green spoke + arrowhead
                dc.setPenWidth(8);
                dc.setColor(COLOR_MAIN, Graphics.COLOR_TRANSPARENT);
                dc.drawLine(spokeStartX, spokeStartY, spokeEndX, spokeEndY);

                // Arrowhead pointing outward along the spoke direction
                var aLen = 13.0;
                var aW   = 8.0;
                var ux = Math.cos(angleRad).toFloat();
                var uy = -Math.sin(angleRad).toFloat();  // screen y is flipped
                var perpX = -uy;
                var perpY =  ux;

                var tx = spokeEndX + (ux * 6.0).toNumber();
                var ty = spokeEndY + (uy * 6.0).toNumber();
                var p1 = [tx, ty];
                var p2 = [(tx - ux * aLen + perpX * aW).toNumber(), (ty - uy * aLen + perpY * aW).toNumber()];
                var p3 = [(tx - ux * (aLen - 4.0)).toNumber(), (ty - uy * (aLen - 4.0)).toNumber()];
                var p4 = [(tx - ux * aLen - perpX * aW).toNumber(), (ty - uy * aLen - perpY * aW).toNumber()];

                dc.setColor(COLOR_MAIN, Graphics.COLOR_TRANSPARENT);
                dc.fillPolygon([p1, p2, p3, p4]);

                // Bright dot at spoke tip to make it pop
                dc.fillCircle(spokeEndX, spokeEndY, 4);

            } else {
                // Inactive exit: thin gray stub
                dc.setPenWidth(3);
                dc.setColor(COLOR_INACTIVE, Graphics.COLOR_TRANSPARENT);
                dc.drawLine(spokeStartX, spokeStartY, spokeEndX, spokeEndY);
                // Small gray dot at tip
                dc.fillCircle(spokeEndX, spokeEndY, 3);
            }
        }

        // ---- CENTER BADGE with exit number ----
        dc.setColor(COLOR_ACCENT, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(cx, cy, innerR);
        dc.setColor(COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(2);
        dc.drawCircle(cx, cy, innerR);

        var exitStr = target.toString();
        var fontH = Graphics.getFontHeight(Graphics.FONT_MEDIUM);
        dc.setColor(COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, cy - (fontH / 2) + 1, Graphics.FONT_MEDIUM, exitStr, Graphics.TEXT_JUSTIFY_CENTER);
    }

    // --- KEEP LEFT (-1) / KEEP RIGHT (+1) ---
    private static function drawKeep(dc as Graphics.Dc, cx as Number, cy as Number, size as Number, dir as Number) as Void {
        // Base trunk
        dc.setPenWidth(8);
        var botY = cy + size - 4;
        var splitY = cy + 8;

        // Dimmed alternative fork
        dc.setColor(COLOR_INACTIVE, Graphics.COLOR_TRANSPARENT);
        var altEndX = cx - (dir * 20);
        var altEndY = cy - size + 16;
        dc.drawLine(cx, splitY, altEndX, altEndY);

        // Active highlighted fork
        dc.setColor(COLOR_MAIN, Graphics.COLOR_TRANSPARENT);
        dc.drawLine(cx, botY, cx, splitY);
        var activeEndX = cx + (dir * 20);
        var activeEndY = cy - size + 16;
        dc.drawLine(cx, splitY, activeEndX, activeEndY);

        // Arrow head on active fork
        var tipX = cx + (dir * 24);
        var tipY = cy - size + 6;
        var pts = [
            [tipX, tipY],
            [tipX - (dir * 14), tipY + 12],
            [tipX, tipY + 7],
            [tipX + (dir * 6), tipY + 14]
        ];
        dc.fillPolygon(pts);
    }

    // --- DESTINATION REACHED ---
    private static function drawDestination(dc as Graphics.Dc, cx as Number, cy as Number, size as Number) as Void {
        // Flag pole
        dc.setPenWidth(4);
        dc.setColor(COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        var poleX = cx - 14;
        var poleTop = cy - size + 4;
        var poleBot = cy + size - 6;
        dc.drawLine(poleX, poleTop, poleX, poleBot);

        // Checkered flag body
        var sqW = 7;
        var sqH = 5;

        for (var row = 0; row < 4; row++) {
            for (var col = 0; col < 4; col++) {
                var isCheck = (row + col) % 2 == 0;
                var color = isCheck ? COLOR_WHITE : COLOR_MAIN;
                dc.setColor(color, Graphics.COLOR_TRANSPARENT);
                dc.fillRectangle(poleX + (col * sqW), poleTop + (row * sqH), sqW, sqH);
            }
        }

        // Base pin / circle
        dc.setColor(COLOR_MAIN, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(poleX, poleBot, 5);
    }

    // --- STANDBY / NO ROUTE ---
    private static function drawStandby(dc as Graphics.Dc, cx as Number, cy as Number, size as Number) as Void {
        var r = size - 8;

        // Outer radar / compass ring
        dc.setPenWidth(2);
        dc.setColor(COLOR_INACTIVE, Graphics.COLOR_TRANSPARENT);
        dc.drawCircle(cx, cy, r);
        dc.drawCircle(cx, cy, (r * 0.55).toNumber());

        // Compass crosshairs
        dc.drawLine(cx - r - 4, cy, cx + r + 4, cy);
        dc.drawLine(cx, cy - r - 4, cx, cy + r + 4);

        // Center North needle
        dc.setColor(COLOR_ACCENT, Graphics.COLOR_TRANSPARENT);
        var northPts = [
            [cx, cy - r + 4],
            [cx - 6, cy],
            [cx + 6, cy]
        ];
        dc.fillPolygon(northPts);

        // Center South needle
        dc.setColor(COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        var southPts = [
            [cx, cy + r - 4],
            [cx - 6, cy],
            [cx + 6, cy]
        ];
        dc.fillPolygon(southPts);

        // Center hub
        dc.setColor(COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(cx, cy, 3);
    }
}

