package com.dhpcs.liquidity.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.ZoneId;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Identicon extends View {

    private static final float COLOUR_SATURATION = 1f;
    private static final float COLOUR_VALUE = 0.8f;

    private static final int GRID_ROW_COUNT = 5;
    private static final int GRID_COLUMN_COUNT = 5;
    private static final int GRID_CENTER_COLUMN_INDEX = GRID_COLUMN_COUNT / 2;

    private final Paint paint;

    private int cellWidth;
    private int cellHeight;

    private int[][] cellColours;

    public Identicon(Context context) {
        this(context, null, 0);
    }

    public Identicon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Identicon(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint = new Paint();
    }

    private int getCellColor(byte[] hash) {
        byte hueMsb = getModuloHashByte(hash, 0);
        byte hueLsb = getModuloHashByte(hash, 1);
        float hue = (float) ((((((0xFF & hueMsb) << 8) | (0xFF & hueLsb)) / 65536d) * 360d));
        return Color.HSVToColor(new float[]{hue, COLOUR_SATURATION, COLOUR_VALUE});
    }

    private int[][] getCellColors(byte[] hash) {
        int[][] result = new int[GRID_ROW_COUNT][GRID_COLUMN_COUNT];
        int color = getCellColor(hash);
        for (int r = 0; r < GRID_ROW_COUNT; r++) {
            for (int c = 0; c < GRID_COLUMN_COUNT; c++) {
                if (isCellVisible(hash, r, c)) {
                    result[r][c] = color;
                }
            }
        }
        return result;
    }

    private boolean getModuloHashBit(byte[] hash, int byteOffset, int index) {
        byte bits = hash[(byteOffset + (index / 8)) % hash.length];
        return (1 & (bits >>> (index % 8))) == 1;
    }

    private byte getModuloHashByte(byte[] hash, int index) {
        return hash[index % hash.length];
    }

    private int getSymmetricColumnIndex(int columnIndex) {
        if (columnIndex < GRID_CENTER_COLUMN_INDEX) {
            return columnIndex;
        } else {
            return GRID_COLUMN_COUNT - (columnIndex + 1);
        }
    }

    private boolean isCellVisible(byte[] hash, int row, int column) {
        return getModuloHashBit(
                hash,
                2,
                (row * GRID_CENTER_COLUMN_INDEX + 1) + getSymmetricColumnIndex(column)
        );
    }

    public void show(ZoneId zoneId, MemberId memberId) {
        byte[] hash;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            messageDigest.update(
                    ByteBuffer.allocate(8).putLong(zoneId.id().getMostSignificantBits()).array()
            );
            messageDigest.update(
                    ByteBuffer.allocate(8).putLong(zoneId.id().getLeastSignificantBits()).array()
            );
            messageDigest.update(
                    ByteBuffer.allocate(4).putInt(memberId.id()).array()
            );
            hash = messageDigest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
        cellColours = getCellColors(hash);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellColours != null) {
            int x, y;
            for (int rowIndex = 0; rowIndex < GRID_ROW_COUNT; rowIndex++) {
                for (int columnIndex = 0; columnIndex < GRID_COLUMN_COUNT; columnIndex++) {
                    x = cellWidth * columnIndex;
                    y = cellHeight * rowIndex;
                    paint.setColor(cellColours[rowIndex][columnIndex]);
                    canvas.drawRect(
                            x,
                            y,
                            x + cellWidth,
                            y + cellHeight,
                            paint
                    );
                }
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cellWidth = w / GRID_COLUMN_COUNT;
        cellHeight = h / GRID_ROW_COUNT;
    }

}
