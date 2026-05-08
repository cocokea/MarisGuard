package com.maris7.guard.antiesp.base;

import java.util.List;

public record BaseTemplate(int anchorX, int anchorY, int anchorZ, List<BaseTemplateBlock> blocks) {
}

