package com.dhpcs.liquidity;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.dhpcs.liquidity.models.Identifier;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Identicon extends View {

    private static final int ROW_COUNT = 5;
    private static final int COLUMN_COUNT = 5;

    private static final int CENTER_COLUMN_INDEX = COLUMN_COUNT / 2;

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
        return Color.rgb(
                getModuloHashByte(hash, 0),
                getModuloHashByte(hash, 1),
                getModuloHashByte(hash, 2)
        );
    }

    private int[][] getCellColors(byte[] hash) {
        int[][] result = new int[ROW_COUNT][COLUMN_COUNT];
        int colorVisible = getCellColor(hash);
        for (int r = 0; r < ROW_COUNT; r++) {
            for (int c = 0; c < COLUMN_COUNT; c++) {
                if (isCellVisible(hash, r, c)) {
                    result[r][c] = colorVisible;
                } else {
                    result[r][c] = 0xFFF0F0F0;
                }
            }
        }
        return result;
    }

    private boolean getModuloHashBit(byte[] hash, int byteOffset, int index) {
        byte bits = hash[(byteOffset + (index / 8)) % hash.length];
        return (1 & (bits >>> (index % 8))) == 1;
    }

    private int getModuloHashByte(byte[] hash, int index) {
        return 0xFF & hash[index % hash.length];
    }

    private int getSymmetricColumnIndex(int columnIndex) {
        if (columnIndex < CENTER_COLUMN_INDEX) {
            return columnIndex;
        } else {
            return COLUMN_COUNT - (columnIndex + 1);
        }
    }

    private boolean isCellVisible(byte[] hash, int row, int column) {
        return getModuloHashBit(
                hash,
                3,
                (row * CENTER_COLUMN_INDEX + 1) + getSymmetricColumnIndex(column)
        );
    }

    public void show(Identifier identifier) {
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-1").digest(identifier.id().toString().getBytes());
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
            for (int rowIndex = 0; rowIndex < ROW_COUNT; rowIndex++) {
                for (int columnIndex = 0; columnIndex < COLUMN_COUNT; columnIndex++) {
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
        cellWidth = w / COLUMN_COUNT;
        cellHeight = h / ROW_COUNT;
    }

}
