package com.robohelper.gametest;

import com.robohelper.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

/** Проверки приёмки, дополняющие игровые сценарии профессий. */
public final class AcceptanceTests implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void bodyRecipeMatchesSpecification(TestContext context) {
        ScreenHandler screen = new ScreenHandler(null, 0) {
            @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean canUse(PlayerEntity player) { return true; }
        };
        CraftingInventory input = new CraftingInventory(screen, 3, 3);
        for (int i = 0; i < 9; i++) input.setStack(i, new ItemStack(Items.IRON_INGOT));
        input.setStack(1, new ItemStack(Items.REDSTONE));
        input.setStack(4, new ItemStack(Items.CHEST));
        CraftingRecipe recipe = (CraftingRecipe) context.getWorld().getRecipeManager()
                .get(new Identifier("robohelper", "robot_body")).orElseThrow();
        context.assertTrue(recipe.matches(input, context.getWorld())
                && recipe.craft(input, context.getWorld().getRegistryManager()).isOf(ModItems.ROBOT_BODY), "Крафт корпуса из семи слитков, редстоуна и сундука");
        input.setStack(4, new ItemStack(Items.BARREL));
        context.assertFalse(recipe.matches(input, context.getWorld()), "Бочка не заменяет сундук в рецепте корпуса");
        context.complete();
    }
}
