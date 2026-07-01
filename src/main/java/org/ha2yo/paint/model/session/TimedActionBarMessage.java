package org.ha2yo.paint.model.session;

import net.kyori.adventure.text.Component;

public record TimedActionBarMessage(Component component, long untilMillis) {
}
