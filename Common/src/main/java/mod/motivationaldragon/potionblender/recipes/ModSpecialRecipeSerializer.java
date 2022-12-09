package mod.motivationaldragon.potionblender.recipes;

import mod.motivationaldragon.potionblender.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;

import java.util.function.BiConsumer;

public class ModSpecialRecipeSerializer {

    public static final SimpleRecipeSerializer<CombinedTippedArrowRecipe> COMBINED_TIPPED_ARROW = new SimpleRecipeSerializer<>(CombinedTippedArrowRecipe::new);



    public static void register(BiConsumer<RecipeSerializer<?>, ResourceLocation> r){
        r.accept(COMBINED_TIPPED_ARROW ,new ResourceLocation(Constants.MOD_ID,"tipped_combined_arrow"));
        Constants.LOG.debug("Loaded recipe");
    }


}
