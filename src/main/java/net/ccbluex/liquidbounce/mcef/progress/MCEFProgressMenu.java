/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2024 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package net.ccbluex.liquidbounce.mcef.progress;

import net.ccbluex.liquidbounce.mcef.MCEF;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class MCEFProgressMenu extends Screen {

    public MCEFProgressMenu(String brand) {
        super(Text.literal(brand + " is downloading required libraries..."));
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        double cx = width / 2d;
        double cy = height / 2d;

        double progressBarHeight = 14;
        double progressBarWidth = width / 3d;

        MatrixStack poseStack = graphics.getMatrices();

        /* Draw Progress Bar */
        poseStack.push();
        poseStack.translate(cx, cy, 0);
        poseStack.translate(-progressBarWidth / 2d, -progressBarHeight / 2d, 0);
        graphics.fill( // bar border
                0, 0,
                (int) progressBarWidth,
                (int) progressBarHeight,
                -1
        );
        graphics.fill( // bar padding
                2, 2,
                (int) progressBarWidth - 2,
                (int) progressBarHeight - 2,
                -16777215
        );
        graphics.fill( // bar
                4, 4,
                (int) ((progressBarWidth - 4) * MCEF.INSTANCE.getResourceManager().progressTracker.getProgress()),
                (int) progressBarHeight - 4,
                -1
        );
        poseStack.pop();

        // putting this here incase I want to re-add a third line later on
        // allows me to generalize the code to not care about line count
        String[] text = new String[]{
                MCEF.INSTANCE.getResourceManager().progressTracker.getTask(),
                Math.round(MCEF.INSTANCE.getResourceManager().progressTracker.getProgress() * 100) + "%",
        };

        /* Draw Text */
        // calculate offset for the top line
        int oSet = ((textRenderer.fontHeight / 2) + ((textRenderer.fontHeight + 2) * (text.length + 2))) + 4;
        poseStack.push();
        poseStack.translate(
                (int) (cx),
                (int) (cy - oSet),
                0
        );
        // draw menu name
        graphics.drawText(
                textRenderer,
                Formatting.GOLD + title.getString(),
                (int) -(textRenderer.getWidth(title.getString()) / 2d),
                -textRenderer.fontHeight - 2,
                0xFFFFFF,
                true
        );
        // draw text
        int index = 0;
        for (String s : text) {
            if (index == 1) {
                poseStack.translate(0, textRenderer.fontHeight + 2, 0);
            }

            poseStack.translate(0, textRenderer.fontHeight + 2, 0);
            graphics.drawText(
                    textRenderer,
                    s,
                    (int) -(textRenderer.getWidth(s) / 2d), 0,
                    0xFFFFFF,
                    false
            );
            index++;
        }
        poseStack.pop();
    }

    @Override
    public void tick() {
        if (MCEF.INSTANCE.getResourceManager().progressTracker.isDone()) {
            close();
            client.setScreen(new TitleScreen());
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

}
