package vini2003.xyz.blackhole.mixin.client;

import dev.monarkhes.myron.api.Myron;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vini2003.xyz.blackhole.BlackHole;
import vini2003.xyz.blackhole.BlackHoleClient;
import vini2003.xyz.blackhole.client.utilities.BlackHoleClientUtilities;
import vini2003.xyz.blackhole.common.components.BlackHoleComponent;
import vini2003.xyz.blackhole.common.components.BlackHoleWorldComponent;
import vini2003.xyz.blackhole.registry.common.BlackHoleComponents;

import java.util.Iterator;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	@Shadow private ClientWorld world;
	
	@Shadow @Final private BufferBuilderStorage bufferBuilders;
	
	private BakedModel blackhole_model;
	
	@Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BufferBuilderStorage;getEntityVertexConsumers()Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;"), method = "render")
	void blackhole_render(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f matrix4f, CallbackInfo ci) {
		if (blackhole_model == null) {
			blackhole_model = Myron.getModel(BlackHole.identifier("models/misc/black_sphere"));
		}
		
		BlackHoleWorldComponent blackHoleWorldComponent = BlackHoleComponents.BLACK_HOLES.get(world);
		
		VertexConsumer consumer = bufferBuilders.getEntityVertexConsumers().getBuffer(RenderLayer.getSolid());
		
		blackHoleWorldComponent.getBlackHoles().forEach(blackHole -> {
			matrices.push();
			
			matrices.translate(blackHole.getPos().getX(), blackHole.getPos().getY(), blackHole.getPos().getZ());
			
			float size = blackHole.getSize();
			
			matrices.scale(size, size, size);
			
			// Render the sphere.
			if (blackhole_model != null) {
				MatrixStack.Entry matricesEntry = matrices.peek();
				
				blackhole_model.getQuads(null, null, world.getRandom()).forEach(quad -> {
					consumer.quad(matricesEntry, quad, 0.0F, 0.0F, 0.0F, 0x00000000, OverlayTexture.DEFAULT_UV);
				});
			}
			
			matrices.pop();
			
			// Black out players inside the black hole.
			if (MinecraftClient.getInstance().player != null && blackHole.getPos().subtract(0, 1.75, 0).distanceTo(MinecraftClient.getInstance().player.getPos()) <= size) {
				BlackHoleClient.isBlackedOut = true;
			}
			
			ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
			
			PlayerEntity player = BlackHoleClientUtilities.getPlayer();
			
			// Move particles towards black hole.
			for (Iterator<BlackHoleComponent.BlackHoleParticle> particleIterator = blackHole.getParticles().iterator(); particleIterator.hasNext(); ) {
				BlackHoleComponent.BlackHoleParticle particle = particleIterator.next();
				
				matrices.push();
				
				double distanceToBlackHole = particle.getPos().distanceTo(blackHole.getPos());
				
				double distanceToPlayer = particle.getPos().distanceTo(player.getPos());
				
				if (distanceToBlackHole < size || distanceToPlayer > 128) {
					particleIterator.remove();
					
					matrices.pop();
					
					continue;
				}
				
				Vec3d pull = particle.getPos().subtract(blackHole.getPos()).normalize().multiply(tickDelta * 0.125F + Math.min(8, (distanceToBlackHole - size)) * 0.025F);
				
				particle.setPos(particle.getPos().subtract(pull));
				
				matrices.translate(particle.getPos().getX(), particle.getPos().getY(), particle.getPos().getZ());
				
				itemRenderer.renderItem(particle.getStack(), ModelTransformation.Mode.NONE, world.getLightLevel(new BlockPos(particle.getPos().getX(), particle.getPos().getY(), particle.getPos().getZ())), OverlayTexture.DEFAULT_UV, matrices, bufferBuilders.getEntityVertexConsumers());
				
				matrices.pop();
			}
		});
		
		bufferBuilders.getEntityVertexConsumers().draw();
	}
}