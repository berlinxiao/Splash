package com.henryxxiao.splash.ui.set;

public class SetItem {
    private String itemName;
    private String itemStatus;
    private int imageId;
    private int imageColor;
    private int backColor;

    public SetItem(String itemName, String itemStatus, int imageId, int imageColor, int backColor) {
        this.itemName = itemName;
        this.itemStatus = itemStatus;
        this.imageId = imageId;
        this.imageColor = imageColor;
        this.backColor = backColor;
    }

    public String getItemName() {
        return itemName;
    }

    public String getItemStatus() {
        return itemStatus;
    }

    public int getImageId() {
        return imageId;
    }

    public int getImageColor() {
        return imageColor;
    }

    public int getBackColor() {
        return backColor;
    }
}
