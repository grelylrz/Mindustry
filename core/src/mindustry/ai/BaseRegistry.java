package mindustry.ai;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.Schematic.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.sandbox.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.meta.*;

import java.io.*;

import static mindustry.Vars.*;

public class BaseRegistry{
    /** cores, sorted by tier */
    public Seq<BasePart> cores = new Seq<>();
    /** parts with no requirement */
    public Seq<BasePart> parts = new Seq<>();
    public ObjectMap<Content, Seq<BasePart>> reqParts = new ObjectMap<>();
    public ObjectMap<Item, OreBlock> ores = new ObjectMap<>();
    public ObjectMap<Item, Floor> oreFloors = new ObjectMap<>();

    public Seq<BasePart> forResource(Content item){
        return reqParts.get(item, Seq::new);
    }

    public void load(){
        cores.clear();
        parts.clear();
        reqParts.clear();

        //load ore types and corresponding items
        for(Block block : content.blocks()){
            if(block instanceof OreBlock ore && ore.itemDrop != null && !ore.wallOre && !ores.containsKey(ore.itemDrop)){
                ores.put(ore.itemDrop, ore);
            }else if(block.isFloor() && block.asFloor().itemDrop != null && !oreFloors.containsKey(block.asFloor().itemDrop)){
                oreFloors.put(block.asFloor().itemDrop, block.asFloor());
            }
        }

        String[] names = Core.files.internal("basepartnames").readString().split("\n");

        for(String name : names){
            try{
                Schematic schem = Schematics.read(Core.files.internal("baseparts/" + name));

                BasePart part = new BasePart(schem);
                Tmp.v1.setZero();
                int drills = 0;

                for(Stile tile : schem.tiles){
                    //keep track of core type
                    if(tile.block instanceof CoreBlock){
                        part.core = tile.block;
                    }

                    //save the required resource based on item source - multiple sources are not allowed
                    if(tile.block instanceof ItemSource){
                        Item config = (Item)tile.config;
                        if(config != null) part.required = config;
                    }

                    //same for liquids - this is not used yet
                    if(tile.block instanceof LiquidSource){
                        Liquid config = (Liquid)tile.config;
                        if(config != null) part.required = config;
                    }

                    //calculate averages
                    if(tile.block instanceof Drill || tile.block instanceof Pump){
                        Tmp.v1.add(tile.x*tilesize + tile.block.offset, tile.y*tilesize + tile.block.offset);
                        drills ++;
                    }
                }
                schem.tiles.removeAll(s -> s.block.buildVisibility == BuildVisibility.sandboxOnly);

                part.tier = schem.tiles.sumf(s -> Mathf.pow(s.block.buildTime / s.block.buildCostMultiplier, 1.4f));

                if(part.core != null){
                    cores.add(part);
                }else if(part.required == null){
                    parts.add(part);
                }

                if(drills > 0){
                    Tmp.v1.scl(1f / drills).scl(1f / tilesize);
                    part.centerX = (int)Tmp.v1.x;
                    part.centerY = (int)Tmp.v1.y;
                }else{
                    part.centerX = part.schematic.width/2;
                    part.centerY = part.schematic.height/2;
                }

                if(part.required != null && part.core == null){
                    reqParts.get(part.required, Seq::new).add(part);
                }

            }catch(IOException e){
                throw new RuntimeException(e);
            }
        }

        cores.sort(b -> b.tier);
        parts.sort();
        reqParts.each((key, arr) -> arr.sort());
    }

    public static class BasePart implements Comparable<BasePart>{
        public final Schematic schematic;

        //offsets for drills
        public int centerX, centerY;

        public @Nullable Content required;
        public @Nullable Block core;

        //total build cost
        public float tier;

        public BasePart(Schematic schematic){
            this.schematic = schematic;
        }

        @Override
        public int compareTo(BasePart other){
            return Float.compare(tier, other.tier);
        }
    }
}
