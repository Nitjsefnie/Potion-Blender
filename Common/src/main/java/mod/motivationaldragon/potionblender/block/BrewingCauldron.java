package mod.motivationaldragon.potionblender.block;

import mod.motivationaldragon.potionblender.blockentities.BrewingCauldronBlockEntity;
import mod.motivationaldragon.potionblender.platform.Service;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@SuppressWarnings("deprecation")
public class BrewingCauldron extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HAS_FLUID = BooleanProperty.create("has_fluid");
    public static final BooleanProperty REDRAW_DUMMY = BooleanProperty.create("redraw");

    private static final VoxelShape INSIDE = box(2.0, 8.0, 2.0, 14.0, 16.0, 14.0);
    protected static final VoxelShape SHAPE = Shapes.join(Shapes.block(), Shapes.or(box(0.0, 0.0, 4.0, 16.0, 3.0, 12.0),
            box(4.0, 0.0, 0.0, 12.0, 3.0, 16.0), box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0), INSIDE), BooleanOp.ONLY_FIRST);

    public BrewingCauldron(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HAS_FLUID, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(REDRAW_DUMMY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(HAS_FLUID,FACING);
        builder.add(REDRAW_DUMMY);
    }



    @Override
    @NotNull
    public InteractionResult use(@NotNull BlockState state, Level world, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!world.isClientSide()) {
            BrewingCauldronBlockEntity brewingCauldronBlockEntity = tryGetBlockEntity(world,pos);
            if(brewingCauldronBlockEntity != null) {
                brewingCauldronBlockEntity.onUseDelegate(state, world, pos, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @NotNull
    public BlockState getStateForPlacement( BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos, RandomSource random) {

        if (random.nextInt(10) == 0) {
            world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.5f + random.nextFloat(), random.nextFloat() * 0.7f + 0.6f);
        }

        createDisplayParticles(world, pos, random, state.getValue(FACING));
        createDisplayParticles(world, pos, random, state.getValue(FACING).getOpposite());

        if (state.getValue(HAS_FLUID)){
            BrewingCauldronBlockEntity brewingCauldronBlockEntity = tryGetBlockEntity(world, pos);
            if(brewingCauldronBlockEntity != null) {
                int color = brewingCauldronBlockEntity.getWaterColor();
                ParticleOptions particle = new DustParticleOptions(Vec3.fromRGB24(color).toVector3f(),1.0f);
                float x =  pos.getX() + random.nextIntBetweenInclusive(2,8)/10f;
                float z = pos.getZ() + random.nextIntBetweenInclusive(2,8)/10f;
                world.addParticle(particle, x, pos.getY() +1d, z,
                        0, 0 ,0);
            }
        }


    }

    private static void createDisplayParticles(Level world, BlockPos pos, RandomSource random, Direction direction) {
        Direction.Axis axis = direction.getAxis();

        double xPos = pos.getX() + 0.5;
        double yPos = pos.getY();
        double zPos = pos.getZ() + 0.5;

        double h = random.nextDouble() * 0.6 - 0.3;
        double xOffset = axis == Direction.Axis.X ? direction.getStepX() * 0.52 : h;
        double j = random.nextDouble() * 6.0 / 16.0;
        double zOffset = axis == Direction.Axis.Z ? direction.getStepZ() * 0.52 : h;
        world.addParticle(ParticleTypes.SMOKE, xPos + xOffset, yPos + j, zPos + zOffset, 0.0, 0.0, 0.0);

    }

    @Override
    public @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState blockState) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
        return SHAPE;
    }
    @Override
    public @NotNull VoxelShape getInteractionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        return INSIDE;
    }

    @Override
    public void fallOn(@NotNull Level world, @NotNull BlockState blockState, @NotNull BlockPos pos, Entity entity, float speed) {

        BrewingCauldronBlockEntity brewingCauldronBlockEntity = tryGetBlockEntity(entity.getLevel(),entity.blockPosition());
        if(brewingCauldronBlockEntity != null && !entity.getLevel().isClientSide()) {
                brewingCauldronBlockEntity.onEntityLandDelegate(entity);
        }
        super.fallOn(world, blockState, pos, entity, speed);
    }


    /**
     * Helper for getting the block entity {@link BrewingCauldronBlockEntity} associated with this {@link BrewingCauldron}
     * @param world the world this block is in
     * @param pos the pos the {@link BrewingCauldronBlockEntity}. Should be the same as the block
     * @return the {@link BlockEntity} attached to this {@link BrewingCauldron}
     */
    @Nullable
    private BrewingCauldronBlockEntity tryGetBlockEntity(Level world, BlockPos pos){
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof BrewingCauldronBlockEntity alchemyMixerBlockEntity ? alchemyMixerBlockEntity : null;

    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return Service.PLATFORM.createPlatformBrewingCauldronBlockEntity(pos,state);
    }

}
