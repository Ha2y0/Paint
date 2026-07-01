package org.ha2yo.paint.model.tool;

import org.bukkit.Material;

public enum Tool {
    PALETTE(0, Material.SHIELD, "색상 팔레트", "색상을 선택합니다", "palette"),
    PENCIL(1, Material.SHIELD, "연필", "우클릭을 누르고 있으면 그립니다", "pencil"),
    ERASER(2, Material.SHIELD, "지우개", "우클릭을 누르고 있으면 지웁니다", "eraser"),
    SPRAY(3, Material.SHIELD, "스프레이", "우클릭을 누르고 있으면 흩뿌려 그립니다", "spray"),
    BUCKET(4, Material.SHIELD, "페인트 통", "닫힌 영역을 채웁니다", "bucket"),
    EYEDROPPER(5, Material.SHIELD, "스포이드", "캔버스에서 색상을 가져옵니다", "eyedropper"),
    CLEAR(6, Material.SHIELD, "전체 지우기", "내 캔버스를 비웁니다", "clear"),
    UNDO(7, Material.SHIELD, "실행 취소", "이전 작업을 되돌립니다", "undo"),
    REDO(8, Material.SHIELD, "다시 실행", "되돌린 작업을 다시 실행합니다", "redo"),
    LINE(0, Material.SHIELD, "직선", "드래그해서 직선을 그립니다", "line"),
    RECTANGLE(1, Material.SHIELD, "사각형", "드래그해서 사각형 테두리를 그립니다", "rectangle"),
    FILLED_RECTANGLE(2, Material.SHIELD, "채운 사각형", "드래그해서 사각형을 채웁니다", "filled_rectangle"),
    TRIANGLE(3, Material.SHIELD, "삼각형", "드래그해서 삼각형 테두리를 그립니다", "triangle"),
    FILLED_TRIANGLE(4, Material.SHIELD, "채운 삼각형", "드래그해서 삼각형을 채웁니다", "filled_triangle"),
    ELLIPSE(5, Material.SHIELD, "원/타원", "드래그해서 원 또는 타원 테두리를 그립니다", "ellipse"),
    FILLED_ELLIPSE(6, Material.SHIELD, "채운 원/타원", "드래그해서 원 또는 타원을 채웁니다", "filled_ellipse"),
    MOVE_SELECTION(7, Material.SHIELD, "선택 이동", "드래그해서 영역을 선택하고 다시 드래그해서 옮깁니다", "move_selection");

    private static final Tool[] BASIC_TOOLS = {
            PALETTE, PENCIL, ERASER, SPRAY, BUCKET, EYEDROPPER, CLEAR, UNDO, REDO
    };
    private static final Tool[] ADVANCED_TOOLS = {
            LINE, RECTANGLE, FILLED_RECTANGLE, TRIANGLE, FILLED_TRIANGLE, ELLIPSE, FILLED_ELLIPSE, MOVE_SELECTION
    };

    private final int slot;
    private final Material material;
    private final String displayName;
    private final String description;
    private final String itemModel;

    Tool(int slot, Material material, String displayName, String description, String itemModel) {
        this.slot = slot;
        this.material = material;
        this.displayName = displayName;
        this.description = description;
        this.itemModel = itemModel;
    }

    public static Tool fromSlot(int slot) {
        return fromSlot(slot, false);
    }

    public static Tool fromSlot(int slot, boolean advanced) {
        Tool[] tools = advanced ? ADVANCED_TOOLS : BASIC_TOOLS;
        for (Tool tool : tools) {
            if (tool.slot == slot) {
                return tool;
            }
        }
        return null;
    }

    public static Tool[] tools(boolean advanced) {
        return advanced ? ADVANCED_TOOLS.clone() : BASIC_TOOLS.clone();
    }

    public boolean isAdvanced() {
        for (Tool tool : ADVANCED_TOOLS) {
            if (tool == this) {
                return true;
            }
        }
        return false;
    }

    public boolean isShapeTool() {
        return switch (this) {
            case LINE, RECTANGLE, FILLED_RECTANGLE, TRIANGLE, FILLED_TRIANGLE, ELLIPSE, FILLED_ELLIPSE -> true;
            default -> false;
        };
    }

    public boolean isSelectionMoveTool() {
        return this == MOVE_SELECTION;
    }

    public Tool defaultToolForSet() {
        return isAdvanced() ? LINE : PENCIL;
    }

    public static Tool defaultTool(boolean advanced) {
        return advanced ? LINE : PENCIL;
    }

    public static Tool fromStoredName(String name) {
        for (Tool tool : values()) {
            if (tool.name().equals(name)) {
                return tool;
            }
        }
        return null;
    }

    public boolean isContinuous() {
        return this == PENCIL || this == ERASER || this == SPRAY;
    }

    public int slot() {
        return slot;
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String itemModel() {
        return itemModel;
    }
}
