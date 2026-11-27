package com.gtnewhorizons.modularnuclear.common.gui;

import java.util.List;

/** A loop machine whose GUI and scanner show these lines, formatted on the server. */
public interface IReadoutLines {

    List<String> getReadoutLines();

    /** A line saying what the machine is for, above its readout in a compact machine's GUI; empty for none. */
    default String getGuiHeadline() {
        return "";
    }
}
