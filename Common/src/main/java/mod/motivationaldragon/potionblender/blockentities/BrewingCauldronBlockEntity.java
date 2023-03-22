package mod.motivationaldragon.potionblender.blockentities;


import mod.motivationaldragon.potionblender.Constants;
import mod.motivationaldragon.potionblender.block.BrewingCauldron;
import mod.motivationaldragon.potionblender.config.ModConfig;
import mod.motivationaldragon.potionblender.item.ModItem;
import mod.motivationaldragon.potionblender.platform.Service;
import mod.motivationaldragon.potionblender.utils.ModNBTKey;
import mod.motivationaldragon.potionblender.utils.ModUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class BrewingCauldronBlockEntity extends BlockEntity {

    private static final String POTION_MIXER_KEY = Constants.MOD_ID + ".PotionBlender";

    /**
     * Hard coded recipe for the cauldron.
     */
    //TODO: might be a good idea to make recipes modifiable without recompiling the mod
    private static final Map<Item, Item> recipes = new HashMap<>(3);

    /**
     * How high should dropped item spawn relative to the block position.
     * Useful to avoid having item spawn by cauldron being thrown horizontally
     */
    private static final double ITEM_DROP_OFFSET = 0.3;

    static {
        recipes.put(Items.NETHER_WART, Items.POTION);
        recipes.put(Items.REDSTONE, Items.POTION);
        recipes.put(Items.GLOWSTONE_DUST, Items.POTION);
        recipes.put(Items.GUNPOWDER, Items.SPLASH_POTION);
        recipes.put(Items.DRAGON_BREATH, Items.LINGERING_POTION);
    }


    /**
     * The cauldron inventory. It is mean to only contain potion
     */
    private NonNullList<ItemStack> inventory;
    /**
     * The current amount of potion in the cauldron. Useful since the inventory size is constant
     */
    private int numberOfPotion;

    protected BrewingCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(Service.PLATFORM.getPlatformBrewingCauldron(), pos, state);
        this.inventory = NonNullList.withSize(ModConfig.getConfig().max_effects, ItemStack.EMPTY);
        this.numberOfPotion = 0;
    }

    public NonNullList<ItemStack> getInventory() {
        return inventory;
    }


    public int size() {
        return this.inventory.size();
    }

    private void emptyCauldron(@NotNull Level level) {
        inventory.clear();
        numberOfPotion = 0;

        BlockState hasFluid = level.getBlockState(this.getBlockPos()).setValue(BrewingCauldron.HAS_FLUID, false);
        level.setBlockAndUpdate(this.getBlockPos(), hasFluid);
        updateListeners();
    }

    private void updateListeners() {
        this.setChanged();
        syncInventoryWithClient();
        assert this.getLevel() != null;
        this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_NEIGHBORS);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Send a packet to sync this block entity inventory with the client
     */
    protected abstract void syncInventoryWithClient();

    /**
     * Delegation from the onEntityLand method in the {@link net.minecraft.world.level.block.Block} class
     * Useful to access data such as inventory attached to the block entity from {@link net.minecraft.world.level.block.Block} callback
     */
    public void onUseDelegate(BlockState _state, Level level, BlockPos pos, Player player) {
        if (numberOfPotion >= 1) {
            dropInventoryContent(level);
        }
    }


    private void craftCombinedPotion(ItemStack recipeItemStack, Level level, @NotNull BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        List<MobEffectInstance> finalPotionStatusEffects = mergeCombinableEffects(this.getInventoryStatusEffectsInstances());

        //read recipe
        ItemStack potionToCraft = new ItemStack(recipes.get(recipeItemStack.getItem()));

        if (recipeItemStack.is(Items.DRAGON_BREATH)) {
            finalPotionStatusEffects = handleLingeringPotions(finalPotionStatusEffects);
        }

        if (recipeItemStack.is(Items.REDSTONE)) {
            finalPotionStatusEffects = handleRedstone(finalPotionStatusEffects, recipeItemStack.getCount());
        }

        if (recipeItemStack.is(Items.GLOWSTONE_DUST)) {
            finalPotionStatusEffects = handleGlowstone(finalPotionStatusEffects, recipeItemStack.getCount());
        }

        //create and drop the potion contained all the effect of the previous potion
        ItemStack potionItemStack = PotionUtils.setCustomEffects(potionToCraft, finalPotionStatusEffects);
        assert potionItemStack.getTag() != null;

        int color = PotionUtils.getColor(finalPotionStatusEffects);

        //Used to force tipped arrow color with the help of mixins
        potionItemStack.getTag().putInt(PotionUtils.TAG_CUSTOM_POTION_COLOR, color);

        Containers.dropItemStack(level, pos.getX(), pos.getY() + ITEM_DROP_OFFSET, pos.getZ(), potionItemStack);

        //Drop all old potion bottle minus the one used for the new potion.
        Containers.dropItemStack(level, pos.getX(), pos.getY() + ITEM_DROP_OFFSET, pos.getZ(), new ItemStack(Items.GLASS_BOTTLE, numberOfPotion - 1));

        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 1.0f);
        emptyCauldron(level);
    }


    /**
     * Merge same effects in a potion. For instance poison 30sec and poison 40sec merge both effect into poison 70sec instead
     */
    private static List<MobEffectInstance> mergeCombinableEffects(List<MobEffectInstance> effectInstances) {

        Collection<MobEffect> mergedStatusEffects = new HashSet<>();
        List<MobEffectInstance> finalPotionStatusEffects = new ArrayList<>(effectInstances);

        // The tricky part here is that a potion type share 1 MobEffectInstance, making it impossible to differentiate them using ==
        // Therefore to test if effectInstance1 == effectInstance2 we use a range based for loop and test indices
        // This is otherwise a simple double iteration where we remember if we have already seen an effect type
        for (int i = 0; i < finalPotionStatusEffects.size(); i++) {
            MobEffectInstance effectInstance1 = finalPotionStatusEffects.get(i);

            List<MobEffectInstance> combinableEffects = new ArrayList<>();

            int totalDuration = effectInstance1.getDuration();
            //Effect are always combinable with themselves
            combinableEffects.add(effectInstance1);

            //This is the inversely proportional gain. First added potion has 1/2 the duration, 2nd 1/3, 3rd 1/4
            //decay = 1/potionDecay
            int potionDecay = 1;

            for (int j = 0; j < finalPotionStatusEffects.size(); j++) {
                MobEffectInstance effectInstance2 = finalPotionStatusEffects.get(j);

                if (i != j && !mergedStatusEffects.contains(effectInstance1.getEffect()) && areEffectsDurationsAddable(effectInstance1, effectInstance2)) {
                    totalDuration += (1.0d / potionDecay) * effectInstance2.getDuration();
                    potionDecay++;
                    combinableEffects.add(effectInstance2);
                }
            }

            mergedStatusEffects.add(effectInstance1.getEffect());

            if (combinableEffects.size() > 1) {
                MobEffectInstance combinedEffect = ModUtils.copyEffectWithNewDuration(combinableEffects.get(0), totalDuration);
                finalPotionStatusEffects.removeAll(combinableEffects);
                finalPotionStatusEffects.add(combinedEffect);
            }
        }
        return finalPotionStatusEffects;
    }


    /**
     * Handle lingering potion lesser duration and potency combination
     * Quoting <a href="https://minecraft.fandom.com/wiki/Lingering_Potion">https://minecraft.fandom.com/wiki/Lingering_Potion</a>:
     * "For finalPotionStatusEffects with duration, the duration applied by the cloud is 1⁄4 that of the corresponding potion."
     * "For finalPotionStatusEffects without duration such as healing or harming, the potency of the effect is 1⁄2 that of the corresponding potion"
     **/
    @NotNull
    private static List<MobEffectInstance> handleLingeringPotions(List<MobEffectInstance> finalPotionStatusEffects) {
        List<MobEffectInstance> lingeringEffects = new ArrayList<>(finalPotionStatusEffects.size());
        for (MobEffectInstance effectInstance : finalPotionStatusEffects) {
            if (effectInstance.getEffect().isInstantenous()) {
                //We are using the full constructor to copy effect witch is why the call is so long
                lingeringEffects.add(new MobEffectInstance(
                        effectInstance.getEffect(),
                        effectInstance.getDuration(),
                        Math.round(effectInstance.getAmplifier() * 0.5f),
                        effectInstance.isAmbient(),
                        effectInstance.isVisible(),
                        effectInstance.showIcon()));
            }
            else {
                lingeringEffects.add(ModUtils.copyEffectWithNewDuration(
                        effectInstance,
                        Math.round(effectInstance.getDuration() * 0.25f)
                ));
            }

        }
        return lingeringEffects;
    }


    /**
     * Handle redstone
     **/
    @NotNull
    private static List<MobEffectInstance> handleRedstone(List<MobEffectInstance> finalPotionStatusEffects,
                                                          int count) {
        List<MobEffectInstance> lingeringEffects = new ArrayList<>(finalPotionStatusEffects.size());
        for (MobEffectInstance effectInstance : finalPotionStatusEffects) {
            if (!effectInstance.getEffect().isInstantenous()) {
                if (effectInstance.getDuration() == Integer.MAX_VALUE) {
                    lingeringEffects.add(effectInstance);
                    continue;
                }
                long duration = effectInstance.getDuration();
                for (int i = 0; i < count; i++) {
                    duration = Math.round(Math.max(1.2 * duration + 1, 1));
                    if (duration > Integer.MAX_VALUE) {
                        duration = Integer.MAX_VALUE;
                        break;
                    }
                }
                lingeringEffects.add(ModUtils.copyEffectWithNewDuration(
                        effectInstance,
                        Math.round((int) duration)
                ));
            }
            else {
                lingeringEffects.add(effectInstance);
            }

        }
        return lingeringEffects;
    }


    /**
     * Handle glowstone
     **/
    @NotNull
    private static List<MobEffectInstance> handleGlowstone(List<MobEffectInstance> finalPotionStatusEffects,
                                                           int count) {
        List<MobEffectInstance> lingeringEffects = new ArrayList<>(finalPotionStatusEffects.size());
        for (MobEffectInstance effectInstance : finalPotionStatusEffects) {
            if (effectInstance.getAmplifier() == 127) {
                lingeringEffects.add(effectInstance);
                continue;
            }
            if (!effectInstance.getEffect().isInstantenous()) {
                lingeringEffects.add(new MobEffectInstance(
                        effectInstance.getEffect(),
                        effectInstance.getDuration() / (int) Math.pow(2, Math.max(0, 127 - count)),
                        Math.min(effectInstance.getAmplifier() + count, 127),
                        effectInstance.isAmbient(),
                        effectInstance.isVisible(),
                        effectInstance.showIcon()));
            }
            else {
                lingeringEffects.add(new MobEffectInstance(
                        effectInstance.getEffect(),
                        effectInstance.getDuration(),
                        Math.min(effectInstance.getAmplifier() + count, 127),
                        effectInstance.isAmbient(),
                        effectInstance.isVisible(),
                        effectInstance.showIcon()));
            }

        }
        return lingeringEffects;
    }

    private static boolean areEffectsDurationsAddable(MobEffectInstance effectInstance1, MobEffectInstance effectInstance2) {
        return effectInstance1.getEffect() == effectInstance2.getEffect() &&
                effectInstance1.getAmplifier() == effectInstance2.getAmplifier();
    }


    private void dropInventoryContent(@NotNull Level level) {
        if (level.isClientSide()) {
            return;
        }
        level.playSound(null, this.getBlockPos(), SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 1.0f, 1.0f);
        Containers.dropContents(level, this.getBlockPos().offset(0, ITEM_DROP_OFFSET, 0), this.getInventory());
        emptyCauldron(level);
    }

    /**
     * Delegation from the onEntityLand method in the {@link net.minecraft.world.level.block.Block} class
     * Useful to access data such as inventory attached to the block entity from {@link net.minecraft.world.level.block.Block} callback
     *
     * @param entity the entity that landed on the attached block
     * @see net.minecraft.world.level.block.Block#fallOn(Level, BlockState, BlockPos, Entity, float) for the full method documentation
     * and ommited parameter
     */
    public void onEntityLandDelegate(Entity entity) {
        assert level != null;
        if (level.isClientSide()) {
            return;
        }

        if (entity instanceof ItemEntity itemEntity) {
            ItemStack itemStack = itemEntity.getItem();

            //Handle overload mechanic where a cauldron explode if a combined potion is thrown into it
            if (isACombinedPotion(itemStack)) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                BlockPos pos = this.getBlockPos();
                this.level.explode(entity, pos.getX(), pos.getY(), pos.getZ(), 1.5F, Level.ExplosionInteraction.BLOCK);
            }


            //Add item
            if (itemStack.is(Items.POTION) && numberOfPotion < inventory.size()) {
                if (wouldIgnoreInstantPotion(itemStack)) return;
                addItemToCauldron(itemEntity);
            }
            //Craft potion
            if (recipes.containsKey(itemStack.getItem()) && numberOfPotion > 0) {
                try {
                    craftCombinedPotion(itemStack, level, this.getBlockPos());
                } catch (NullPointerException e) {
                    emptyCauldron(level);
                    return;
                }
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
    }

    private boolean wouldIgnoreInstantPotion(ItemStack itemStack) {
        List<MobEffectInstance> effectInstances = PotionUtils.getMobEffects(itemStack);
        effectInstances = effectInstances
                .stream()
                .filter(e -> e.getEffect().isInstantenous()).toList();
        return this.getInventoryStatusEffectsInstances()
                .stream()
                .anyMatch(effectInstances::contains);
    }


    private static boolean isACombinedPotion(ItemStack itemStack) {
        boolean isTippedArrow = false;
        if (itemStack.hasTag()) {
            assert itemStack.getTag() != null;
            isTippedArrow = itemStack.getTag().contains(ModNBTKey.IS_TIPPED_ARROW_COMBINED_KEY);
        }
        return itemStack.is(ModItem.COMBINED_POTION) ||
                itemStack.is(ModItem.COMBINED_SPLASH_POTION) ||
                itemStack.is(ModItem.COMBINED_LINGERING_POTION) ||
                isTippedArrow;

    }


    /**
     * Add an item entity to the cauldron inventory
     *
     * @param itemEntity the item entity
     */
    private void addItemToCauldron(@NotNull ItemEntity itemEntity) {

        assert level != null;
        if (level.isClientSide()) {
            return;
        }

        Level level = itemEntity.getLevel();

        level.playSound(null, this.getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0f, 1.0f);

        //add potion to cauldron inventory
        addItem(itemEntity.getItem());

        //Since we added a potion, the cauldron must now appear with fluid
        BlockState mixerCauldronBlockState = level.getBlockState(this.getBlockPos()).setValue(BrewingCauldron.HAS_FLUID, true);
        level.setBlockAndUpdate(this.getBlockPos(), mixerCauldronBlockState);

        //To force re-rendering of the block tint
        forceChunkUpdate();

        itemEntity.remove(Entity.RemovalReason.DISCARDED);

        updateListeners();
    }

    private void addItem(@NotNull ItemStack itemStack) {
        assert level != null;
        //Check for incoherent state if inventory has changed since last Level load
        if (numberOfPotion > inventory.size()) {
            countPotion(inventory);
        }
        inventory.set(numberOfPotion, itemStack);
        numberOfPotion++;
    }

    /**
     * Force a chunk rerender by toggling a block state back and forth
     * Used when an item is added to force rendering of the block tint
     */
    private void forceChunkUpdate() {
        if (level == null) {
            return;
        }
        BlockState blockState = level.getBlockState(this.getBlockPos());

        boolean redrawValue = blockState.getValue(BrewingCauldron.REDRAW_DUMMY);

        BlockState newRedraw = blockState.setValue(BrewingCauldron.REDRAW_DUMMY, !redrawValue);
        level.setBlockAndUpdate(this.getBlockPos(), newRedraw);
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        this.inventory = NonNullList.withSize(this.size(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt, this.inventory);
        numberOfPotion = nbt.getInt(POTION_MIXER_KEY);
        super.load(nbt);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbt) {
        ContainerHelper.saveAllItems(nbt, inventory);
        nbt.putInt(POTION_MIXER_KEY, numberOfPotion);
        super.saveAdditional(nbt);
    }

    public int getWaterColor() {
        return PotionUtils.getColor(getInventoryStatusEffectsInstances());
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @NotNull
    protected List<MobEffectInstance> getInventoryStatusEffectsInstances() {
        List<MobEffectInstance> effects = new ArrayList<>();

        //Check for incoherent state if inventory has changed since last Level load
        if (this.numberOfPotion > this.inventory.size()) {
            this.numberOfPotion = this.size() - 1;
        }

        for (int i = 0; i < this.numberOfPotion; i++) {
            ItemStack potionItemStack = inventory.get(i);
            effects.addAll(PotionUtils.getMobEffects(potionItemStack));
        }
        return effects;
    }

    public void setInventory(NonNullList<ItemStack> newInventory) {
        this.numberOfPotion = 0;
        this.inventory = newInventory;
        countPotion(newInventory);

    }

    private void countPotion(NonNullList<ItemStack> newInventory) {
        for (ItemStack stack : newInventory) {
            if (!stack.is(Items.AIR)) {
                numberOfPotion++;
            }
        }
    }
}
