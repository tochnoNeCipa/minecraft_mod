package com.robohelper.client;

import com.robohelper.RoboHelper;
import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/** Стандартная гуманоидная модель с текстурой робота размером 64×64. */
public final class RobotRenderer extends MobEntityRenderer<RobotEntity, BipedEntityModel<RobotEntity>> {
    private static final Identifier TEXTURE = new Identifier(RoboHelper.MOD_ID, "textures/entity/robot.png");

    public RobotRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)),
                RobotConfig.SHADOW_RADIUS);
    }

    @Override
    public Identifier getTexture(RobotEntity entity) {
        return TEXTURE;
    }
}
