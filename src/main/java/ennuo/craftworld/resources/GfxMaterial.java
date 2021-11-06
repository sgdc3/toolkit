package ennuo.craftworld.resources;

import ennuo.craftworld.memory.Data;
import ennuo.craftworld.memory.ResourcePtr;
import ennuo.craftworld.resources.enums.RType;
import ennuo.craftworld.resources.structs.gfxmaterial.Box;
import ennuo.craftworld.resources.structs.gfxmaterial.ParameterAnimation;
import ennuo.craftworld.resources.structs.gfxmaterial.Wire;
import ennuo.craftworld.serializer.v2.Serializable;
import ennuo.craftworld.serializer.v2.Serializer;
import ennuo.toolkit.utilities.Globals;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

public class GfxMaterial implements Serializable {
    public int flags;
    public float alphaTestLevel;
    public byte alphaLayer, alphaMode, shadowCastMode;
    public float bumpLevel, cosinePower,
    reflectionBlur, refractiveIndex,
    refractiveFresnelFalloffPower, refractiveFresnelMultiplier,
    refractiveFresnelOffset, refractiveFresnelShift;
    public byte fuzzLengthAndRefractiveFlag, translucencyDensity,
    fuzzSwirlAngle, fuzzSwirlAmplitude, fuzzLightingBias,
    fuzzLightingScale, iridescenceRoughness;

    byte[][] shaders;
    public ResourcePtr[] textures;

    public byte[] wrapS, wrapT;

    public Box[] boxes;
    public Wire[] wires;

    public int soundEnum;

    public ParameterAnimation[] parameterAnimations;
    
    public GfxMaterial() {}
    public GfxMaterial(Data data) {
        Serializer serializer = new Serializer(data);
        this.serialize(serializer, this);
    }
    
    public GfxMaterial serialize(Serializer serializer, Serializable structure) {
        
        GfxMaterial gfxMaterial = null;
        if (structure != null) gfxMaterial = (GfxMaterial) structure;
        else gfxMaterial = new GfxMaterial();
        
        gfxMaterial.flags = serializer.i32(gfxMaterial.flags);
        gfxMaterial.alphaTestLevel = serializer.f32(gfxMaterial.alphaTestLevel);
        gfxMaterial.alphaLayer = serializer.i8(gfxMaterial.alphaLayer);
        if (serializer.revision > 0x331)
            gfxMaterial.alphaMode = serializer.i8(gfxMaterial.alphaMode);
        gfxMaterial.shadowCastMode = serializer.i8(gfxMaterial.shadowCastMode);
        gfxMaterial.bumpLevel = serializer.f32(gfxMaterial.bumpLevel);
        gfxMaterial.cosinePower = serializer.f32(gfxMaterial.cosinePower);
        gfxMaterial.reflectionBlur = serializer.f32(gfxMaterial.reflectionBlur);
        gfxMaterial.refractiveIndex = serializer.f32(gfxMaterial.refractiveIndex);
        if (serializer.revision > 0x13003ef) {
            gfxMaterial.refractiveFresnelFalloffPower = serializer.f32(gfxMaterial.refractiveFresnelFalloffPower);
            gfxMaterial.refractiveFresnelMultiplier = serializer.f32(gfxMaterial.refractiveFresnelMultiplier);
            gfxMaterial.refractiveFresnelOffset = serializer.f32(gfxMaterial.refractiveFresnelOffset);
            gfxMaterial.refractiveFresnelShift = serializer.f32(gfxMaterial.refractiveFresnelShift);
            gfxMaterial.fuzzLengthAndRefractiveFlag = serializer.i8(gfxMaterial.fuzzLengthAndRefractiveFlag);
            if (serializer.revision > 0x17703ef) {
                gfxMaterial.translucencyDensity = serializer.i8(gfxMaterial.translucencyDensity);
                gfxMaterial.fuzzSwirlAngle = serializer.i8(gfxMaterial.fuzzSwirlAngle);
                gfxMaterial.fuzzSwirlAmplitude = serializer.i8(gfxMaterial.fuzzSwirlAmplitude);
                gfxMaterial.fuzzLightingBias = serializer.i8(gfxMaterial.fuzzLightingBias);
                gfxMaterial.fuzzLightingScale = serializer.i8(gfxMaterial.fuzzLightingScale);
                gfxMaterial.iridescenceRoughness = serializer.i8(gfxMaterial.iridescenceRoughness);
            }
        }
        
        if (serializer.isWriting) {
            int offset = 0;
            if (serializer.revision < 0x398) 
                serializer.output.i32(0);
            for (byte[] shader : gfxMaterial.shaders) {
                offset += shader.length;
                serializer.output.i32(offset);
            }
            for (byte[] shader : gfxMaterial.shaders)
                serializer.output.bytes(shader);
            for (int i = 0; i < 8; ++i)
                serializer.output.resource(gfxMaterial.textures[i]);            
        } else {
            int shaderCount = 3;
            if (serializer.revision == 0x3e2) shaderCount = 25;
            else if (serializer.revision >= 0x398) shaderCount = 11;
            else if (serializer.revision >= 0x353) shaderCount = 8;
            else if (serializer.revision == 0x272 || serializer.revision >= 0x336) shaderCount = 4;
            
            gfxMaterial.shaders = new byte[shaderCount][];
            
            int[] offsets = new int[shaderCount + 1];
            for (int i = (serializer.revision >= 0x398) ? 1 : 0; i < shaderCount + 1; ++i)
                offsets[i] = serializer.input.i32();
            for (int i = 1; i <= shaderCount; ++i)
                gfxMaterial.shaders[i - 1] = serializer.input.bytes(offsets[i] - offsets[i - 1]);
            
            gfxMaterial.textures = new ResourcePtr[8];
            for (int i = 0; i < 8; ++i)
                gfxMaterial.textures[i] = serializer.input.resource(RType.TEXTURE);
        }
        
        gfxMaterial.wrapS = serializer.i8a(gfxMaterial.wrapS);
        gfxMaterial.wrapT = serializer.i8a(gfxMaterial.wrapT);
        gfxMaterial.boxes = serializer.array(gfxMaterial.boxes, Box.class);
        gfxMaterial.wires = serializer.array(gfxMaterial.wires, Wire.class);
        
        if (serializer.revision >= 0x129)
            gfxMaterial.soundEnum = serializer.i32(gfxMaterial.soundEnum);
        
        if (serializer.revision >= 0x2a2)
            gfxMaterial.parameterAnimations = 
                        serializer.array(gfxMaterial.parameterAnimations, ParameterAnimation.class);
        
        return gfxMaterial;
    }
    
    public Wire findWireFrom(int box) {
        for (Wire wire: this.wires)
            if (wire.boxFrom == box)
                return wire;
        return null;
    }
    
    public int getOutputBox() {
        for (int i = 0; i < this.boxes.length; ++i) {
            Box box = this.boxes[i];
            if (box.type == Box.BoxType.OUTPUT)
                return i;
        }
        return -1;
    }
    
    public Box getBoxFrom(Wire wire) {
        return this.boxes[wire.boxFrom];
    }
    
    public Box getBoxTo(Wire wire) {
        return this.boxes[wire.boxTo];
    }
    
    public byte[] extractTexture(int index) {
        byte[] data = Globals.extractFile(this.textures[index]);
        if (data == null) return null;
        Texture texture = new Texture(data);
        if (texture.parsed) {
            BufferedImage image = texture.getImage();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                ImageIO.write(image, "png", baos);
                return baos.toByteArray();
            } catch (IOException ex) {
                Logger.getLogger(GfxMaterial.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
        }
        return null;
    }
}
