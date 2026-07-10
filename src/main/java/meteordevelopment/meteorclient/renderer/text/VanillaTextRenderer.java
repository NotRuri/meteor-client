/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer.text;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.Font.GlyphVisitor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.LinkedHashMap;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class VanillaTextRenderer implements TextRenderer {
    public static final VanillaTextRenderer INSTANCE = new VanillaTextRenderer();

    private final StagedVertexBuffer stagedBuffer = new StagedVertexBuffer(() -> "VanillaTextRenderer", 2048);
    private final Map<RenderType, StagedVertexBuffer.Draw> currentDraws = new LinkedHashMap<>();
    private final Map<RenderType, VertexConsumer> currentConsumers = new LinkedHashMap<>();

    private final PoseStack matrices = new PoseStack();
    private final Matrix4f emptyMatrix = new Matrix4f();

    public double scale = 2;
    public boolean scaleIndividually;

    private boolean building;
    private double alpha = 1;

    private VanillaTextRenderer() {
        // Use INSTANCE
    }

    @Override
    public void setAlpha(double a) {
        alpha = a;
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text.isEmpty()) return 0;

        if (length != text.length()) text = text.substring(0, length);
        return (mc.font.width(text) + (shadow ? 1 : 0)) * scale;
    }

    @Override
    public double getHeight(boolean shadow) {
        return (mc.font.lineHeight + (shadow ? 1 : 0)) * scale;
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        if (building) throw new RuntimeException("VanillaTextRenderer.begin() called twice");

        currentDraws.clear();
        currentConsumers.clear();

        this.scale = scale * 2;
        this.building = true;
    }

    @Override
    public double render(String text, double x, double y, Color color, boolean shadow) {
        boolean wasBuilding = building;
        if (!wasBuilding) begin();

        x += 0.5 * scale;
        y += 0.5 * scale;

        int preA = color.a;
        color.a = (int) (((double) color.a / 255 * alpha) * 255);

        Matrix4f matrix = emptyMatrix;
        if (scaleIndividually) {
            matrices.pushPose();
            matrices.scale((float) scale, (float) scale, 1);
            matrix = matrices.last().pose();
        }

        float finalX = (float) (x / scale);
        float finalY = (float) (y / scale);
        int finalColor = color.getPacked();
        Matrix4f finalMatrix = matrix;

        mc.font.prepareText(text, finalX, finalY, finalColor, shadow, LightCoordsUtil.FULL_BRIGHT)
            .visit(new GlyphVisitor() {
                @Override
                public void acceptRenderable(TextRenderable renderable) {
                    RenderType type = renderable.renderType(DisplayMode.NORMAL);
                    VertexConsumer consumer = currentConsumers.computeIfAbsent(type, t -> {
                        StagedVertexBuffer.Draw draw = stagedBuffer.appendDraw(t.format(), t.primitiveTopology());
                        currentDraws.put(t, draw);
                        return stagedBuffer.getVertexBuilder(draw);
                    });
                    renderable.render(finalMatrix, consumer, LightCoordsUtil.FULL_BRIGHT, shadow);
                }
            });

        double x2 = (x / scale) + mc.font.width(text);

        if (scaleIndividually) matrices.popPose();

        color.a = preA;

        if (!wasBuilding) end();
        return (x2 - 1) * scale;
    }

    @Override
    public boolean isBuilding() {
        return building;
    }

    @Override
    public void end() {
        if (!building) throw new RuntimeException("VanillaTextRenderer.end() called without calling begin()");

        Matrix4fStack matrixStack = RenderSystem.getModelViewStack();

        matrixStack.pushMatrix();
        if (!scaleIndividually) matrixStack.scale((float) scale, (float) scale, 1);

        stagedBuffer.upload();
        for (Map.Entry<RenderType, StagedVertexBuffer.Draw> entry : currentDraws.entrySet()) {
            RenderType type = entry.getKey();
            StagedVertexBuffer.Draw draw = entry.getValue();
            StagedVertexBuffer.ExecuteInfo executeInfo = stagedBuffer.getExecuteInfo(draw);
            if (executeInfo != null) {
                type.prepare().drawFromBuffer(executeInfo);
            }
        }
        stagedBuffer.endDraw();
        currentDraws.clear();
        currentConsumers.clear();

        matrixStack.popMatrix();

        this.scale = 2;
        this.building = false;
    }
}