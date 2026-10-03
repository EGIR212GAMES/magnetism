package ru.magnetism.copier;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ru.magnetism.registry.ModBlocks;

public final class CopierMenu extends AnvilMenu {
    private int copyCost;

    public CopierMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(containerId, playerInventory, access);
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.COPIER);
    }

    @Override
    public void createResult() {
        ItemStack source = this.inputSlots.getItem(0);
        ItemStack secondInput = this.inputSlots.getItem(1);

        this.copyCost = 0;
        this.resultSlots.setItem(0, ItemStack.EMPTY);

        if (!secondInput.isEmpty() || !CopierService.isSupported(source)) {
            this.broadcastChanges();
            return;
        }

        int cost = CopyCostCalculator.calculate(source);
        ItemStack copy = CopierService.createCopy(source);
        if (cost <= 0 || copy.isEmpty()) {
            this.broadcastChanges();
            return;
        }

        this.copyCost = cost;
        this.resultSlots.setItem(0, copy);
        this.broadcastChanges();
    }

    @Override
    public int getCost() {
        return copyCost;
    }

    @Override
    public boolean setItemName(String name) {
        // The Copier copies the source stack byte-for-byte through ItemStack.copy().
        // It is not a renaming station, so suppress Anvil name changes completely.
        return false;
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        if (!hasStack) {
            return false;
        }

        ItemStack source = this.inputSlots.getItem(0);
        ItemStack expected = CopierService.createCopy(source);
        int cost = CopyCostCalculator.calculate(source);

        if (cost <= 0 || expected.isEmpty() || !ItemStack.matches(this.resultSlots.getItem(0), expected)) {
            return false;
        }

        if (player.hasInfiniteMaterials()) {
            return true;
        }

        return player.experienceLevel >= cost;
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        ItemStack source = this.inputSlots.getItem(0);
        ItemStack expected = CopierService.createCopy(source);
        int cost = CopyCostCalculator.calculate(source);

        // Validate again at the actual server-side take point. A client can issue
        // clicks in an order that leaves the container state briefly stale.
        if (cost <= 0 || expected.isEmpty() || !ItemStack.matches(stack, expected)) {
            this.createResult();
            return;
        }

        if (!player.hasInfiniteMaterials() && player.experienceLevel < cost) {
            this.createResult();
            return;
        }

        if (!player.hasInfiniteMaterials()) {
            player.giveExperienceLevels(-cost);
        }

        this.inputSlots.removeItem(0, 1);
        this.copyCost = 0;
        this.createResult();
    }
}
