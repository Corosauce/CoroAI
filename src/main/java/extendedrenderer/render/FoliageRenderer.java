package extendedrenderer.render;

import CoroUtil.config.ConfigCoroAI;
import CoroUtil.util.CoroUtilBlockLightCache;
import extendedrenderer.foliage.Foliage;
import extendedrenderer.foliage.ParticleTallGrassTemp;
import extendedrenderer.particle.ParticleRegistry;
import extendedrenderer.particle.ShaderManager;
import extendedrenderer.shader.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;

public class FoliageRenderer {

    private static final ResourceLocation PARTICLE_TEXTURES = new ResourceLocation("textures/particle/particles.png");

    private final TextureManager renderer;

    public static FloatBuffer projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
    public static FloatBuffer viewMatrixBuffer = BufferUtils.createFloatBuffer(16);

    public Transformation transformation;

    public boolean needsUpdate = true;

    public List<Foliage> listFoliage = new ArrayList<>();

    public FoliageRenderer(TextureManager rendererIn) {
        this.renderer = rendererIn;
        transformation = new Transformation();
    }

    public void render(Entity entityIn, float partialTicks)
    {

        if (RotatingParticleManager.useShaders) {

            Minecraft mc = Minecraft.getMinecraft();
            EntityRenderer er = mc.entityRenderer;
            World world = mc.world;

            Foliage.interpPosX = entityIn.lastTickPosX + (entityIn.posX - entityIn.lastTickPosX) * (double)partialTicks;
            Foliage.interpPosY = entityIn.lastTickPosY + (entityIn.posY - entityIn.lastTickPosY) * (double)partialTicks;
            Foliage.interpPosZ = entityIn.lastTickPosZ + (entityIn.posZ - entityIn.lastTickPosZ) * (double)partialTicks;

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.alphaFunc(516, 0.003921569F);

            GlStateManager.disableCull();

            GlStateManager.depthMask(true);

            renderJustShaders(entityIn, partialTicks);

            GlStateManager.enableCull();

            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.alphaFunc(516, 0.1F);
        }
    }

    public void renderJustShaders(Entity entityIn, float partialTicks)
    {

        Minecraft mc = Minecraft.getMinecraft();
        EntityRenderer er = mc.entityRenderer;
        World world = mc.world;

        Matrix4fe projectionMatrix = new Matrix4fe();
        FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, buf);
        buf.rewind();
        Matrix4fe.get(projectionMatrix, 0, buf);

        //modify far distance, 4x as far
        //dont use for now, see RotatingParticleManager notes
        boolean distantRendering = false;
        if (distantRendering) {
            float zNear = 0.05F;
            float zFar = (float) (mc.gameSettings.renderDistanceChunks * 16) * 4F;
            projectionMatrix.m22 = ((zFar + zNear) / (zNear - zFar));
            projectionMatrix.m32 = ((zFar + zFar) * zNear / (zNear - zFar));
        }

        Matrix4fe viewMatrix = new Matrix4fe();
        FloatBuffer buf2 = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buf2);
        buf2.rewind();
        Matrix4fe.get(viewMatrix, 0, buf2);

        //m03, m13, m23 ?
        //30 31 32
        //bad context to do in i think...
        /*viewMatrix.m30 = (float) entityIn.posX;
        viewMatrix.m31 = (float) entityIn.posY;
        viewMatrix.m32 = (float) entityIn.posZ;*/

        //Matrix4fe modelViewMatrix = projectionMatrix.mul(viewMatrix);

        //fix for camera model matrix being 0 0 0 on positions, readjusts to world positions for static rendering instanced meshes
        float interpX = (float)(entityIn.prevPosX + (entityIn.posX - entityIn.prevPosX) * partialTicks);
        float interpY = (float)(entityIn.prevPosY + (entityIn.posY - entityIn.prevPosY) * partialTicks);
        float interpZ = (float)(entityIn.prevPosZ + (entityIn.posZ - entityIn.prevPosZ) * partialTicks);
        Matrix4fe matrixFix = new Matrix4fe();
        matrixFix = matrixFix.translationRotateScale(
                -interpX, -interpY, -interpZ,
                0, 0, 0, 1,
                1, 1, 1);

        boolean test1 = false;

        ShaderProgram shaderProgram = ShaderEngine.renderer.getShaderProgram("foliage");
        //transformation = ShaderEngine.renderer.transformation;
        shaderProgram.bind();

        //testing determined i can save frames by baking projectionMatrix into modelViewMatrixCamera, might have to revert for more complex shaders
        //further testing its just barely faster, if at all...
        //shaderProgram.setUniform("projectionMatrix", mat);
        if (test1) {
            try {
                shaderProgram.setUniformEfficient("projectionMatrix", projectionMatrix, projectionMatrixBuffer);
            } catch (Exception ex) {
                //ignore optimization in testing
            }
        }


        if (test1) {
            matrixFix = viewMatrix.mul(matrixFix);
        } else {
            Matrix4fe modelViewMatrix = projectionMatrix.mul(viewMatrix);
            matrixFix = modelViewMatrix.mul(matrixFix);
        }

        shaderProgram.setUniformEfficient("modelViewMatrixCamera", matrixFix, viewMatrixBuffer);

        shaderProgram.setUniform("texture_sampler", 0);

        try {
            shaderProgram.setUniform("time", (int) world.getTotalWorldTime());
        } catch (Exception ex) {
            //ignore optimization in testing
        }

        shaderProgram.setUniform("partialTick", partialTicks);

        MeshBufferManagerFoliage.setupMeshIfMissing(ParticleRegistry.tallgrass);
        InstancedMeshFoliage mesh = MeshBufferManagerFoliage.getMesh(ParticleRegistry.tallgrass);

        mesh.initRender();
        mesh.initRenderVBO1();
        mesh.initRenderVBO2();


        boolean skipUpdate = false;
        boolean updateFoliageObjects = false;
        boolean updateVBO1 = true;
        boolean updateVBO2 = false;

        if (!skipUpdate || needsUpdate) {
            //also resets position
            mesh.instanceDataBuffer.clear();
            mesh.instanceDataBufferSeldom.clear();
            mesh.curBufferPos = 0;
        }

        BlockPos pos = entityIn.getPosition();



        Random rand = new Random();
        rand.setSeed(5);
        int range = 150;

        int amount = 70000;
        int adjAmount = amount;

        boolean subTest = false;

        if (subTest) {
            adjAmount = amount;
            //adjAmount = 50;
        }

        CoroUtilBlockLightCache.brightnessPlayer = CoroUtilBlockLightCache.getBrightnessNonLightmap(world, (float)entityIn.posX, (float)entityIn.posY, (float)entityIn.posZ);

        if (!skipUpdate || needsUpdate) {
            if (updateFoliageObjects || needsUpdate) {
                //make obj
                listFoliage.clear();
                int foliageAdded = 0;
                for (int i = 0; i < adjAmount; i++) {
                    Foliage foliage = new Foliage();
                    int randX = rand.nextInt(range) - range / 2;
                    int randY = 0;//rand.nextInt(range) - range / 2;
                    int randZ = rand.nextInt(range) - range / 2;
                    foliage.setPosition(new BlockPos(pos).up(0).add(randX, randY, randZ));
                    foliage.posY += 0.5F;
                    foliage.prevPosY = foliage.posY;
                    foliage.posX += rand.nextFloat();
                    foliage.prevPosX = foliage.posX;
                    foliage.posZ += rand.nextFloat();
                    foliage.prevPosZ = foliage.posZ;
                    foliage.rotationYaw = rand.nextInt(360);
                    //foliage.rotationPitch = rand.nextInt(90) - 45;
                    foliage.particleScale /= 0.2;

                    //foliage.particleScale *= 3;

                    BlockPos folipos = new BlockPos(foliage.posX, foliage.posY, foliage.posZ);

                    IBlockState state = entityIn.world.getBlockState(folipos.down());
                    if (state.getMaterial() == Material.GRASS) {
                        int color = Minecraft.getMinecraft().getBlockColors().colorMultiplier(state, entityIn.world, folipos, 0);
                        foliage.particleRed = (float) (color >> 16 & 255) / 255.0F;
                        foliage.particleGreen = (float) (color >> 8 & 255) / 255.0F;
                        foliage.particleBlue = (float) (color & 255) / 255.0F;

                        foliage.brightnessCache = CoroUtilBlockLightCache.brightnessPlayer;

                        listFoliage.add(foliage);
                    }
                }
            }

            if (updateVBO1 || needsUpdate) {
                for (Foliage foliage : listFoliage) {

                    double dist = entityIn.getDistance(foliage.posX, foliage.posY, foliage.posZ);
                    if (false && dist < 10) {
                        foliage.particleAlpha = (float)dist / 10F;
                    } else {
                        foliage.particleAlpha = 1F;
                    }

                    foliage.brightnessCache = CoroUtilBlockLightCache.brightnessPlayer + 0.2F;

                    //update vbo1
                    foliage.renderForShaderVBO1(mesh, transformation, viewMatrix, entityIn, partialTicks);
                }

                if (!subTest) {
                    mesh.instanceDataBuffer.limit(mesh.curBufferPos * mesh.INSTANCE_SIZE_FLOATS);
                } else {
                    mesh.instanceDataBuffer.limit(adjAmount * mesh.INSTANCE_SIZE_FLOATS);
                }

                OpenGlHelper.glBindBuffer(GL_ARRAY_BUFFER, mesh.instanceDataVBO);

                if (!subTest) {
                    ShaderManager.glBufferData(GL_ARRAY_BUFFER, mesh.instanceDataBuffer, GL_DYNAMIC_DRAW);
                } else {
                    GL15.glBufferSubData(GL_ARRAY_BUFFER, 0, mesh.instanceDataBuffer);
                    //GL15.glMapBuffer()
                }
            }

            mesh.curBufferPos = 0;

            if (updateVBO2 || needsUpdate) {
                for (Foliage foliage : listFoliage) {
                    foliage.updateQuaternion(entityIn);

                    //update vbo2
                    foliage.renderForShaderVBO2(mesh, transformation, viewMatrix, entityIn, partialTicks);
                }

                //wasnt used in particle renderer and even crashes it :o
                if (!subTest) {
                    mesh.instanceDataBufferSeldom.limit(mesh.curBufferPos * mesh.INSTANCE_SIZE_FLOATS_SELDOM);
                } else {
                    mesh.instanceDataBufferSeldom.limit(adjAmount * mesh.INSTANCE_SIZE_FLOATS_SELDOM);
                }

                OpenGlHelper.glBindBuffer(GL_ARRAY_BUFFER, mesh.instanceDataVBOSeldom);

                if (!subTest) {
                    ShaderManager.glBufferData(GL_ARRAY_BUFFER, mesh.instanceDataBufferSeldom, GL_DYNAMIC_DRAW);
                } else {
                    GL15.glBufferSubData(GL_ARRAY_BUFFER, 0, mesh.instanceDataBufferSeldom);
                }
            }
        }

        needsUpdate = false;

        ShaderManager.glDrawElementsInstanced(GL_TRIANGLES, mesh.getVertexCount(), GL_UNSIGNED_INT, 0, listFoliage.size());

        OpenGlHelper.glBindBuffer(GL_ARRAY_BUFFER, 0);

        mesh.endRenderVBO1();
        mesh.endRenderVBO2();
        mesh.endRender();

        ShaderEngine.renderer.getShaderProgram("foliage").unbind();
    }

}