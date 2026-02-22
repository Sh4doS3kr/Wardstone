package com.moonlight.coreprotect.finishers;

import org.bukkit.*;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

public class FinisherEffects {
    private final Plugin plugin;
    public static final String GHOST_TAG = "finisher_ghost";
    public FinisherEffects(Plugin plugin) { this.plugin = plugin; }

    public int play(FinisherType type, Player victim, Player killer) {
        switch (type) {
            case THUNDER_JUDGMENT: return playThunder(victim);
            case VOID_INVOCATION: return playVoid(victim);
            case BLOOD_ERUPTION: return playBlood(victim);
            case SHATTERED_AMETHYST: return playAmethyst(victim);
            case ORBITAL_STRIKE: return playOrbital(victim);
            case HELLFIRE: return playHellfire(victim);
            case ICE_STORM: return playIce(victim);
            case DRAGON_WRATH: return playDragon(victim);
            case SOUL_VORTEX: return playSoulVortex(victim);
            case WITHER_STORM: return playWitherStorm(victim);
            case SCULK_RESONANCE: return playSculkResonance(victim);
            default: return 40;
        }
    }

    private Location vLoc(Player v) { return v.isOnline() ? v.getLocation() : null; }
    private ThreadLocalRandom rng() { return ThreadLocalRandom.current(); }
    private Material rm(Material[] m) { return m[rng().nextInt(m.length)]; }

    private FallingBlock gb(World w, Location l, Material m, double vx, double vy, double vz, int life) {
        FallingBlock fb = w.spawnFallingBlock(l, m.createBlockData());
        fb.setDropItem(false); fb.setHurtEntities(false);
        fb.setMetadata(GHOST_TAG, new FixedMetadataValue(plugin, true));
        fb.setVelocity(new Vector(vx, vy, vz));
        new BukkitRunnable(){public void run(){if(!fb.isDead()){w.spawnParticle(Particle.CLOUD,fb.getLocation(),4,0.2,0.2,0.2,0.01);fb.remove();}}}.runTaskLater(plugin, life);
        return fb;
    }
    private FallingBlock gf(World w, Location l, Material m, double vx, double vy, double vz, int life) {
        FallingBlock fb = w.spawnFallingBlock(l, m.createBlockData());
        fb.setDropItem(false); fb.setHurtEntities(false); fb.setGravity(false);
        fb.setMetadata(GHOST_TAG, new FixedMetadataValue(plugin, true));
        fb.setVelocity(new Vector(vx, vy, vz));
        new BukkitRunnable(){public void run(){if(!fb.isDead()){w.spawnParticle(Particle.CLOUD,fb.getLocation(),3,0.15,0.15,0.15,0.01);fb.remove();}}}.runTaskLater(plugin, life);
        return fb;
    }
    private void ge(World w, Location l, Material m, double vx, double vy, double vz, int life) {
        FallingBlock fb = w.spawnFallingBlock(l, m.createBlockData());
        fb.setDropItem(false); fb.setHurtEntities(false);
        fb.setMetadata(GHOST_TAG, new FixedMetadataValue(plugin, true));
        fb.setVelocity(new Vector(vx, vy, vz));
        new BukkitRunnable(){public void run(){if(!fb.isDead()){Location d=fb.getLocation();w.spawnParticle(Particle.EXPLOSION_EMITTER,d,1,0.15,0.15,0.15,0);w.spawnParticle(Particle.CLOUD,d,6,0.3,0.3,0.3,0.02);w.playSound(d,Sound.ENTITY_GENERIC_EXPLODE,0.25f,1.5f);fb.remove();}}}.runTaskLater(plugin, life);
    }

    // === 1. THUNDER — ELECTRIC CAGE (ground, 200t) ===
    private int playThunder(Player victim) {
        final int D=200; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        w.playSound(o,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,1.2f,0.3f); w.playSound(o,Sound.ENTITY_WARDEN_EMERGE,0.6f,1.2f);
        Material[] bm={Material.COPPER_BLOCK,Material.OXIDIZED_COPPER,Material.WEATHERED_COPPER,Material.CUT_COPPER,Material.LIGHTNING_ROD,Material.IRON_BLOCK};
        new BukkitRunnable(){int t=0;public void run(){
            if(t>=80||!victim.isOnline()){cancel();return;} Location l=vLoc(victim);if(l==null){cancel();return;}
            double r=4.0-t*0.03;
            for(int i=0;i<32;i++){double a=(Math.PI*2/32)*i+t*0.1;w.spawnParticle(Particle.ELECTRIC_SPARK,l.clone().add(Math.cos(a)*r,0.1,Math.sin(a)*r),4,0.06,0.03,0.06,0.015);}
            if(t%4==0)for(int p=0;p<8;p++){double a=(Math.PI*2/8)*p+t*0.04;for(double y=0;y<4;y+=0.4)w.spawnParticle(Particle.ELECTRIC_SPARK,l.clone().add(Math.cos(a)*r,y,Math.sin(a)*r),3,0.03,0.1,0.03,0.01);}
            if(t%6==0)for(int p=0;p<8;p++){double a=(Math.PI*2/8)*p;double yy=(t/6)*0.6;if(yy>4)yy=4;gf(w,l.clone().add(Math.cos(a)*r,yy,Math.sin(a)*r),rm(bm),0,0,0,130);}
            if(t%4==0){w.spawnParticle(Particle.CLOUD,l.clone().add(0,5,0),15,3,0.3,3,0.02);w.spawnParticle(Particle.DUST,l.clone().add(0,5.5,0),10,3.5,0.4,3.5,0,new Particle.DustOptions(Color.fromRGB(25,25,45),4.0f));}
            if(t%12==0)w.playSound(l,Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,0.8f,1.5f+t*0.01f);
            t+=2;
        }}.runTaskTimer(plugin,0,2);
        int[] bolts={15,25,35,45,55,65,75,85,98,110,120,130,140,150};
        for(int i=0;i<bolts.length;i++){final int idx=i;new BukkitRunnable(){public void run(){
            Location l=vLoc(victim);if(l==null)return;double sp2=4.0*(1.0-idx/(double)bolts.length);double a=rng().nextDouble(Math.PI*2);
            Location st=l.clone().add(Math.cos(a)*sp2,0,Math.sin(a)*sp2);w.strikeLightningEffect(st);
            w.spawnParticle(Particle.ELECTRIC_SPARK,st.clone().add(0,0.5,0),60+idx*5,1.2,2,1.2,0.1);
            for(int b=0;b<4+idx/2;b++)gb(w,st.clone().add(0,0.5,0),rm(bm),rng().nextDouble(-0.8,0.8),rng().nextDouble(0.3,1.0),rng().nextDouble(-0.8,0.8),45+rng().nextInt(20));
        }}.runTaskLater(plugin,bolts[idx]);}
        new BukkitRunnable(){public void run(){
            Location l=vLoc(victim);if(l==null)return;for(int i=0;i<6;i++)w.strikeLightningEffect(l);
            w.spawnParticle(Particle.ELECTRIC_SPARK,l.clone().add(0,0.5,0),600,6,3,6,0.25);w.spawnParticle(Particle.CLOUD,l,150,4,2,4,0.1);
            w.spawnParticle(Particle.EXPLOSION_EMITTER,l,8,2,0.5,2,0);w.spawnParticle(Particle.FLASH,l,5,0,0,0,0);
            w.playSound(l,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,2.0f,0.3f);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,2.0f,0.6f);
            for(int b=0;b<50;b++){double a=(Math.PI*2/50)*b;double sp=0.7+rng().nextDouble(1.0);ge(w,l.clone().add(0,0.5,0),rm(bm),Math.cos(a)*sp,rng().nextDouble(0.15,0.6),Math.sin(a)*sp,48+rng().nextInt(22));}
        }}.runTaskLater(plugin,165);
        new BukkitRunnable(){int t=0;public void run(){if(t>=30){cancel();return;}Location l=vLoc(victim);if(l==null)l=o;w.spawnParticle(Particle.ELECTRIC_SPARK,l,50-t*2,5,0.5,5,0.04);if(t%6==0)w.playSound(l,Sound.ENTITY_LIGHTNING_BOLT_IMPACT,0.4f,1.5f);t+=3;}}.runTaskTimer(plugin,170,3);
        return D;
    }

    // === 2. VOID — SPINNING VORTEX (ground+spin, 200t) ===
    private int playVoid(Player victim) {
        final int D=200; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        w.playSound(o,Sound.ENTITY_ENDERMAN_TELEPORT,1.2f,0.2f); w.playSound(o,Sound.ENTITY_WARDEN_EMERGE,1.0f,0.4f);
        Material[] bm={Material.OBSIDIAN,Material.CRYING_OBSIDIAN,Material.END_STONE,Material.PURPLE_CONCRETE,Material.BLACK_CONCRETE,Material.END_STONE_BRICKS};
        new BukkitRunnable(){int t=0;public void run(){
            if(t>=50||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}
            double r=0.5+t*0.09;
            for(int i=0;i<24;i++){double a=(Math.PI*2/24)*i+t*0.15;w.spawnParticle(Particle.DRAGON_BREATH,l.clone().add(Math.cos(a)*r,0.1,Math.sin(a)*r),3,0.04,0.02,0.04,0.002);w.spawnParticle(Particle.SQUID_INK,l.clone().add(Math.cos(a)*r*0.5,0.15,Math.sin(a)*r*0.5),2,0.05,0.02,0.05,0.005);}
            w.spawnParticle(Particle.PORTAL,l.clone().add(0,0.3,0),20,r*0.4,0.1,r*0.4,0.6);
            if(t%8==0){w.playSound(l,Sound.BLOCK_PORTAL_AMBIENT,0.7f,0.3f+t*0.02f);for(int b=0;b<3;b++){double a2=rng().nextDouble(Math.PI*2);gf(w,l.clone().add(Math.cos(a2)*r,0.1,Math.sin(a2)*r),rm(bm),0,0,0,60);}}
            t+=2;
        }}.runTaskTimer(plugin,0,2);
        new BukkitRunnable(){int t=0;float spin=12f;public void run(){if(t>=155||!victim.isOnline()){cancel();return;}Location l=victim.getLocation();spin=Math.min(spin+0.9f,100f);l.setYaw(l.getYaw()+spin);victim.teleport(l);if(t%3==0)w.spawnParticle(Particle.PORTAL,l.clone().add(0,1,0),12,0.3,0.5,0.3,0.7);t++;}}.runTaskTimer(plugin,28,1);
        new BukkitRunnable(){int t=0;public void run(){
            if(t>=135||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}
            for(int arm=0;arm<8;arm++){double a=t*0.55+arm*Math.PI/4;double maxR=4.0-t*0.025;if(maxR<0.3)maxR=0.3;for(double d=0.3;d<maxR;d+=0.45)w.spawnParticle(Particle.DRAGON_BREATH,l.clone().add(Math.cos(a+d*0.3)*d,0.12,Math.sin(a+d*0.3)*d),2,0.03,0.02,0.03,0.002);}
            w.spawnParticle(Particle.SQUID_INK,l.clone().add(0,0.5,0),12,0.25,0.4,0.25,0.025);
            if(t%6==0)for(int b=0;b<6;b++){double a2=rng().nextDouble(Math.PI*2);double dist=3.5+rng().nextDouble(3);gb(w,l.clone().add(Math.cos(a2)*dist,0.5,Math.sin(a2)*dist),rm(bm),-Math.cos(a2)*0.25,rng().nextDouble(0.05,0.18),-Math.sin(a2)*0.25,30+rng().nextInt(15));}
            if(t%14==0){w.playSound(l,Sound.ENTITY_WARDEN_HEARTBEAT,1.0f,0.4f);w.spawnParticle(Particle.SONIC_BOOM,l,1,0,0,0,0);}
            t+=2;
        }}.runTaskTimer(plugin,35,2);
        new BukkitRunnable(){public void run(){
            Location l=vLoc(victim);if(l==null)return;
            w.spawnParticle(Particle.SQUID_INK,l,400,6,2,6,0.2);w.spawnParticle(Particle.DRAGON_BREATH,l,300,5,1.5,5,0.1);w.spawnParticle(Particle.PORTAL,l,600,6,3,6,2.5);
            w.spawnParticle(Particle.EXPLOSION_EMITTER,l,6,1.5,0.5,1.5,0);w.spawnParticle(Particle.SONIC_BOOM,l,4,1.5,0.5,1.5,0);
            w.playSound(l,Sound.ENTITY_WARDEN_SONIC_BOOM,1.5f,0.3f);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,1.5f,0.4f);
            for(int b=0;b<50;b++){double a=(Math.PI*2/50)*b;double sp=rng().nextDouble(0.5,1.4);ge(w,l.clone().add(0,0.5,0),rm(bm),Math.cos(a)*sp,rng().nextDouble(0.1,0.5),Math.sin(a)*sp,48+rng().nextInt(22));}
        }}.runTaskLater(plugin,172);
        return D;
    }

    // === 3. BLOOD — GEYSERS + RAIN (ground, 200t) ===
    private int playBlood(Player victim) {
        final int D=200; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        w.playSound(o,Sound.ENTITY_WARDEN_EMERGE,0.8f,0.7f);w.playSound(o,Sound.ENTITY_ELDER_GUARDIAN_CURSE,0.5f,0.5f);
        Material[] bm={Material.RED_CONCRETE,Material.RED_CONCRETE_POWDER,Material.REDSTONE_BLOCK,Material.NETHER_WART_BLOCK,Material.RED_WOOL,Material.RED_MUSHROOM_BLOCK,Material.CRIMSON_PLANKS};
        new BukkitRunnable(){int t=0;public void run(){
            if(t>=60||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}
            double r=0.5+t*0.1;
            for(int i=0;i<28;i++){double a=(Math.PI*2/28)*i;for(double d=0.3;d<r;d+=0.5)w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*d,0.05,Math.sin(a)*d),1,0.05,0.01,0.05,0,new Particle.DustOptions(Color.fromRGB(120+rng().nextInt(60),0,0),2.2f));}
            if(t%4==0)for(int b=0;b<4;b++){double a=rng().nextDouble(Math.PI*2);double d=rng().nextDouble(0.3,r);gf(w,l.clone().add(Math.cos(a)*d,-0.3,Math.sin(a)*d),rm(bm),0,0,0,100);}
            if(t%6==0)w.playSound(l,Sound.BLOCK_HONEY_BLOCK_SLIDE,0.8f,0.3f);t+=2;
        }}.runTaskTimer(plugin,0,2);
        new BukkitRunnable(){int t=0;public void run(){
            if(t>=120||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}
            if(t%4==0)for(int g2=0;g2<8;g2++){double a=(Math.PI*2/8)*g2+t*0.03;double gr=2.5+Math.sin(t*0.06+g2)*0.6;Location gey=l.clone().add(Math.cos(a)*gr,0,Math.sin(a)*gr);double h=2+Math.sin(t*0.12+g2)*2.5;for(double y=0;y<h;y+=0.25)w.spawnParticle(Particle.DUST,gey.clone().add(0,y,0),4,0.05,0.03,0.05,0,new Particle.DustOptions(Color.fromRGB(180,0,0),2.2f));if(t%8==0)gb(w,gey.clone().add(0,0.3,0),rm(bm),0,rng().nextDouble(0.4,0.9),0,50);}
            w.spawnParticle(Particle.DUST,l.clone().add(0,0.6,0),15,1.8,0.3,1.8,0,new Particle.DustOptions(Color.RED,1.6f));
            if(t%8==0)for(int b=0;b<4;b++){double a=rng().nextDouble(Math.PI*2);double br=1+rng().nextDouble(2.5);gb(w,l.clone().add(Math.cos(a)*br,-0.3,Math.sin(a)*br),rm(bm),rng().nextDouble(-0.1,0.1),rng().nextDouble(0.35,0.9),rng().nextDouble(-0.1,0.1),50);}
            if(t%10==0)w.playSound(l,Sound.ENTITY_SLIME_SQUISH,1.0f,0.4f);t+=2;
        }}.runTaskTimer(plugin,35,2);
        new BukkitRunnable(){int c=0;public void run(){if(c>=35||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}for(int b=0;b<3;b++){double ox=rng().nextDouble(-4,4),oz=rng().nextDouble(-4,4);gb(w,l.clone().add(ox,6+rng().nextDouble(4),oz),rm(bm),0,0,0,55+rng().nextInt(15));}c++;}}.runTaskTimer(plugin,60,3);
        new BukkitRunnable(){public void run(){
            Location l=vLoc(victim);if(l==null)return;
            w.spawnParticle(Particle.DUST,l,500,6,3,6,0,new Particle.DustOptions(Color.RED,3.5f));w.spawnParticle(Particle.DUST,l,300,5,4,5,0,new Particle.DustOptions(Color.fromRGB(139,0,0),2.8f));
            w.spawnParticle(Particle.EXPLOSION_EMITTER,l,6,2,0.5,2,0);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,1.5f,0.5f);w.playSound(l,Sound.ENTITY_SLIME_SQUISH,1.5f,0.2f);
            for(int b=0;b<55;b++){double a=(Math.PI*2/55)*b;double sp=0.5+rng().nextDouble(0.9);ge(w,l.clone().add(0,0.3,0),rm(bm),Math.cos(a)*sp,rng().nextDouble(0.15,0.7),Math.sin(a)*sp,48+rng().nextInt(22));}
        }}.runTaskLater(plugin,168);
        return D;
    }

    // === 4. AMETHYST — CRYSTAL PRISON (ground, 200t) ===
    private int playAmethyst(Player victim) {
        final int D=200; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        w.playSound(o,Sound.BLOCK_AMETHYST_BLOCK_RESONATE,1.2f,0.4f);w.playSound(o,Sound.BLOCK_BEACON_ACTIVATE,0.6f,1.8f);
        Material[] bm={Material.AMETHYST_BLOCK,Material.AMETHYST_CLUSTER,Material.PURPUR_BLOCK,Material.PURPLE_STAINED_GLASS,Material.PURPUR_PILLAR};
        new BukkitRunnable(){int t=0;public void run(){
            if(t>=80||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}
            if(t%4==0){double yy=(t/4)*0.45;if(yy>5)yy=5;for(int p=0;p<6;p++){double a=(Math.PI*2/6)*p;gf(w,l.clone().add(Math.cos(a)*2.2,yy,Math.sin(a)*2.2),rm(bm),0,0,0,140);if(yy>1.5){double a2=a+Math.PI/6;gf(w,l.clone().add(Math.cos(a2)*1.9,yy-0.3,Math.sin(a2)*1.9),rm(bm),0,0,0,140);}}}
            for(int ring=0;ring<4;ring++){int pts=10+ring*3;for(int i=0;i<pts;i++){double a=(Math.PI*2/pts)*i+t*(0.12+ring*0.06);double r=2.4-ring*0.25;w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*r,ring*0.6+0.2,Math.sin(a)*r),2,0.05,0.06,0.05,0,new Particle.DustOptions(Color.fromRGB(163,73,223),1.8f));}}
            if(t%8==0){w.playSound(l,Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.8f,0.5f+t*0.012f);w.spawnParticle(Particle.END_ROD,l.clone().add(0,1,0),8,1.2,1.0,1.2,0.03);}t+=2;
        }}.runTaskTimer(plugin,0,2);
        new BukkitRunnable(){int t=0;public void run(){
            if(t>=75||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}
            double r=2.2-t*0.018;if(r<0.6)r=0.6;
            for(int i=0;i<30;i++){double a=(Math.PI*2/30)*i+t*0.08;double y=(i%8)*0.5;w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*r,y,Math.sin(a)*r),3,0.03,0.03,0.03,0,new Particle.DustOptions(Color.fromRGB(200,150,255),1.6f));}
            w.spawnParticle(Particle.END_ROD,l.clone().add(0,1,0),8,0.5,0.8,0.5,0.03);
            if(t%10==0)for(int b=0;b<3;b++)gf(w,l.clone().add(rng().nextDouble(-1,1),rng().nextDouble(0.5,3),rng().nextDouble(-1,1)),Material.AMETHYST_CLUSTER,0,0.02,0,55);
            if(t%6==0)w.playSound(l,Sound.BLOCK_AMETHYST_CLUSTER_STEP,0.7f,0.8f+t*0.012f);t+=2;
        }}.runTaskTimer(plugin,80,2);
        new BukkitRunnable(){public void run(){
            Location l=vLoc(victim);if(l==null)return;Location c=l.clone().add(0,1,0);
            w.spawnParticle(Particle.DUST,c,500,6,6,6,0,new Particle.DustOptions(Color.fromRGB(163,73,223),3.5f));w.spawnParticle(Particle.DUST,c,300,5,5,5,0,new Particle.DustOptions(Color.fromRGB(200,150,255),2.8f));
            w.spawnParticle(Particle.END_ROD,c,150,4,4,4,0.25);w.spawnParticle(Particle.EXPLOSION_EMITTER,c,5,1.5,1.5,1.5,0);w.spawnParticle(Particle.FLASH,c,4,0,0,0,0);
            w.playSound(l,Sound.BLOCK_GLASS_BREAK,2.0f,0.3f);w.playSound(l,Sound.BLOCK_AMETHYST_BLOCK_BREAK,2.0f,0.4f);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,1.2f,1.5f);
            for(int b=0;b<55;b++){double a=(Math.PI*2/55)*b;double sp=0.6+rng().nextDouble(1.2);ge(w,c,rm(bm),Math.cos(a)*sp,rng().nextDouble(0.2,1.5),Math.sin(a)*sp,46+rng().nextInt(25));}
        }}.runTaskLater(plugin,158);
        new BukkitRunnable(){int t=0;public void run(){if(t>=35){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}w.spawnParticle(Particle.DUST,l.clone().add(0,1,0),30-t,4,4,4,0,new Particle.DustOptions(Color.fromRGB(200,150,255),1.0f));w.spawnParticle(Particle.END_ROD,l.clone().add(0,1,0),8,3,3,3,0.03);if(t%8==0)w.playSound(l,Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.4f,1.5f);t+=3;}}.runTaskTimer(plugin,163,3);
        return D;
    }

    // === 5. ORBITAL — SKY BEAM (sky, 240t) ===
    private int playOrbital(Player victim) {
        final int D=240; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        Material[] bm={Material.GLOWSTONE,Material.SEA_LANTERN,Material.GOLD_BLOCK,Material.QUARTZ_BLOCK,Material.WHITE_CONCRETE,Material.YELLOW_CONCRETE};
        new BukkitRunnable(){int t=0;public void run(){if(t>=70||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}double r=3.5-t*0.03;for(int arm=0;arm<8;arm++){double a=(Math.PI/4)*arm+t*0.12;for(double d=0.3;d<r;d+=0.35)w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*d,0.1,Math.sin(a)*d),2,0.03,0.01,0.03,0,new Particle.DustOptions(Color.RED,1.3f));}w.spawnParticle(Particle.END_ROD,l.clone().add(0,0.3,0),6,0.2,0.05,0.2,0.01);w.spawnParticle(Particle.DUST,l.clone().add(0,0.2,0),10,0.4,0.05,0.4,0,new Particle.DustOptions(Color.ORANGE,1.8f));if(t%12==0){w.playSound(l,Sound.BLOCK_BEACON_AMBIENT,0.7f,1.0f+t*0.02f);w.playSound(l,Sound.BLOCK_NOTE_BLOCK_PLING,0.4f,1.5f+t*0.01f);}t+=2;}}.runTaskTimer(plugin,0,2);
        new BukkitRunnable(){int t=0;public void run(){if(t>=60||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}double maxY=15+t*0.5;for(double y=0;y<maxY;y+=0.8){double wb=Math.sin((y+t)*0.4)*0.12;w.spawnParticle(Particle.END_ROD,l.clone().add(wb,y,wb),2,0.04,0.1,0.04,0.003);}if(t%6==0)for(int b=0;b<3;b++)gb(w,l.clone().add(rng().nextDouble(-1,1),0,rng().nextDouble(-1,1)),rm(bm),0,rng().nextDouble(0.4,1.0),0,55);if(t%8==0)w.playSound(l,Sound.BLOCK_BEACON_ACTIVATE,0.5f,1.5f+t*0.01f);t+=2;}}.runTaskTimer(plugin,40,2);
        new BukkitRunnable(){public void run(){if(!victim.isOnline())return;victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,160,2,false,false,false));Location l=vLoc(victim);if(l!=null){w.playSound(l,Sound.ITEM_TRIDENT_THUNDER,1.0f,1.5f);w.spawnParticle(Particle.FLASH,l,3,0,0,0,0);}}}.runTaskLater(plugin,70);
        new BukkitRunnable(){int t=0;public void run(){if(t>=100||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}for(double y=-2;y<35;y+=0.5){double wb=Math.sin((y+t)*0.25)*0.15;w.spawnParticle(Particle.END_ROD,l.clone().add(wb,y,wb),3,0.12,0.08,0.12,0.005);if(y<5&&t%2==0)w.spawnParticle(Particle.DUST,l.clone().add(0,y,0),3,0.25,0.08,0.25,0,new Particle.DustOptions(Color.WHITE,2.5f));}if(t%4==0){double a=t*0.3;for(int b=0;b<4;b++){double ba=a+b*Math.PI/2;gf(w,l.clone().add(Math.cos(ba)*2.5,1+Math.sin(t*0.1)*0.5,Math.sin(ba)*2.5),rm(bm),0,0.02,0,20);}}if(t%8==0)for(int b=0;b<2;b++)gb(w,l.clone().add(rng().nextDouble(-2,2),-1,rng().nextDouble(-2,2)),rm(bm),0,rng().nextDouble(0.5,1.2),0,50);if(t%6==0)w.playSound(l,Sound.BLOCK_BEACON_AMBIENT,0.8f,2.0f);t+=2;}}.runTaskTimer(plugin,100,2);
        new BukkitRunnable(){public void run(){Location l=vLoc(victim);if(l==null)return;w.spawnParticle(Particle.EXPLOSION_EMITTER,l,10,2.5,2.5,2.5,0);w.spawnParticle(Particle.END_ROD,l,500,7,7,7,0.3);w.spawnParticle(Particle.FLASH,l,8,1,1,1,0);w.spawnParticle(Particle.DUST,l,300,6,6,6,0,new Particle.DustOptions(Color.WHITE,3.5f));w.spawnParticle(Particle.DUST,l,200,5,5,5,0,new Particle.DustOptions(Color.YELLOW,2.8f));w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,2.0f,0.5f);w.playSound(l,Sound.ITEM_TRIDENT_THUNDER,2.0f,0.4f);for(int b=0;b<50;b++)ge(w,l.clone().add(0,1.5,0),rm(bm),rng().nextDouble(-2.2,2.2),rng().nextDouble(0.5,2.8),rng().nextDouble(-2.2,2.2),52+rng().nextInt(25));}}.runTaskLater(plugin,205);
        new BukkitRunnable(){int t=0;public void run(){if(t>=28){cancel();return;}Location l=vLoc(victim);if(l==null)l=o.clone().add(0,4,0);for(int f=0;f<4;f++){Location fl=l.clone().add(rng().nextDouble(-5,5),rng().nextDouble(-1,6),rng().nextDouble(-5,5));w.spawnParticle(Particle.END_ROD,fl,25,1,1,1,0.12);}if(t%3==0){w.playSound(l,Sound.ENTITY_FIREWORK_ROCKET_BLAST,0.8f,0.8f+t*0.03f);w.playSound(l,Sound.ENTITY_FIREWORK_ROCKET_TWINKLE,0.6f,1.0f+t*0.02f);}t+=3;}}.runTaskTimer(plugin,210,3);
        return D;
    }

    // === 6. HELLFIRE — LAVA LAKE + FIRE COLUMNS (ground, 200t) ===
    private int playHellfire(Player victim) {
        final int D=200; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        w.playSound(o,Sound.ENTITY_BLAZE_AMBIENT,1.2f,0.3f);w.playSound(o,Sound.ENTITY_WARDEN_EMERGE,0.8f,0.6f);
        Material[] bm={Material.MAGMA_BLOCK,Material.NETHERRACK,Material.NETHER_BRICKS,Material.ORANGE_CONCRETE,Material.RED_CONCRETE,Material.CRIMSON_NYLIUM};
        new BukkitRunnable(){int t=0;public void run(){if(t>=70||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}double r=1+t*0.06;for(int i=0;i<20;i++){double a=(Math.PI*2/20)*i+t*0.08;w.spawnParticle(Particle.FLAME,l.clone().add(Math.cos(a)*r,0.12,Math.sin(a)*r),4,0.06,0.1,0.06,0.01);w.spawnParticle(Particle.LAVA,l.clone().add(Math.cos(a)*r*0.5,0.06,Math.sin(a)*r*0.5),1,0.04,0.02,0.04,0);}w.spawnParticle(Particle.DUST,l.clone().add(0,0.06,0),12,r*0.5,0.02,r*0.5,0,new Particle.DustOptions(Color.fromRGB(200,50,0),2.2f));if(t%4==0)for(int b=0;b<4;b++){double a=rng().nextDouble(Math.PI*2);double d=rng().nextDouble(0.3,r);gf(w,l.clone().add(Math.cos(a)*d,-0.3,Math.sin(a)*d),rm(bm),0,0,0,100);}if(t%8==0){w.playSound(l,Sound.ENTITY_BLAZE_SHOOT,0.6f,0.5f);for(int b=0;b<3;b++){double a=rng().nextDouble(Math.PI*2);double dr=rng().nextDouble(0.5,r);gb(w,l.clone().add(Math.cos(a)*dr,-0.3,Math.sin(a)*dr),rm(bm),0,rng().nextDouble(0.3,0.8),0,60);}}t+=2;}}.runTaskTimer(plugin,0,2);
        new BukkitRunnable(){int t=0;public void run(){if(t>=115||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}for(int p=0;p<8;p++){double a=(Math.PI*2/8)*p+t*0.015;Location pil=l.clone().add(Math.cos(a)*2.8,0,Math.sin(a)*2.8);double h=2+Math.sin(t*0.1+p*0.8)*2.5;for(double y=0;y<h;y+=0.3)w.spawnParticle(Particle.FLAME,pil.clone().add(0,y,0),3,0.05,0.04,0.05,0.008);}if(t%8==0)for(int p=0;p<8;p++){double a=(Math.PI*2/8)*p;double yy=(t/8)*0.5;if(yy>4)yy=4;gf(w,l.clone().add(Math.cos(a)*2.8,yy,Math.sin(a)*2.8),rm(bm),0,0,0,90);}w.spawnParticle(Particle.LAVA,l,4,2,0.3,2,0);if(t%6==0)w.playSound(l,Sound.BLOCK_FIRE_AMBIENT,0.8f,0.5f);if(t%20==0)w.playSound(l,Sound.ENTITY_BLAZE_AMBIENT,0.6f,0.4f);t+=2;}}.runTaskTimer(plugin,45,2);
        new BukkitRunnable(){public void run(){Location l=vLoc(victim);if(l==null)return;w.spawnParticle(Particle.FLAME,l,500,6,3,6,0.2);w.spawnParticle(Particle.LAVA,l,120,5,1.5,5,0);w.spawnParticle(Particle.DUST,l,250,5,3,5,0,new Particle.DustOptions(Color.fromRGB(255,60,0),3.5f));w.spawnParticle(Particle.EXPLOSION_EMITTER,l,8,2,0.5,2,0);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,2.0f,0.4f);w.playSound(l,Sound.ENTITY_BLAZE_DEATH,1.5f,0.3f);for(int b=0;b<55;b++){double a=(Math.PI*2/55)*b;double sp=0.6+rng().nextDouble(1.0);ge(w,l.clone().add(0,0.5,0),rm(bm),Math.cos(a)*sp,rng().nextDouble(0.2,0.9),Math.sin(a)*sp,48+rng().nextInt(25));}}}.runTaskLater(plugin,168);
        return D;
    }

    // === 7. ICE — MASSIVE SPIKE LINES (ground, 200t) ===
    private int playIce(Player victim) {
        final int D=200; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        w.playSound(o,Sound.BLOCK_GLASS_BREAK,1.0f,1.8f);w.playSound(o,Sound.ENTITY_PLAYER_HURT_FREEZE,1.0f,0.5f);
        Material[] bm={Material.BLUE_ICE,Material.PACKED_ICE,Material.ICE,Material.LIGHT_BLUE_CONCRETE,Material.WHITE_CONCRETE,Material.LIGHT_BLUE_STAINED_GLASS};
        // Frost floor
        new BukkitRunnable(){int t=0;public void run(){if(t>=35||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}double r=0.5+t*0.14;for(int i=0;i<28;i++){double a=(Math.PI*2/28)*i;w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*r,0.06,Math.sin(a)*r),2,0.05,0.01,0.05,0,new Particle.DustOptions(Color.fromRGB(150,220,255),2.2f));w.spawnParticle(Particle.SNOWFLAKE,l.clone().add(Math.cos(a)*r*0.5,0.2,Math.sin(a)*r*0.5),1,0.06,0.08,0.06,0.008);}if(t%4==0)for(int b=0;b<5;b++){double a=rng().nextDouble(Math.PI*2);double d=rng().nextDouble(0.3,r);gf(w,l.clone().add(Math.cos(a)*d,-0.3,Math.sin(a)*d),rm(bm),0,0,0,120);}if(t%6==0)w.playSound(l,Sound.BLOCK_GLASS_BREAK,0.5f,2.0f);t+=2;}}.runTaskTimer(plugin,0,2);
        // 12 spike lines of ice blocks
        new BukkitRunnable(){int t=0;public void run(){if(t>=105||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}double sL=0.8+t*0.07;for(int s=0;s<12;s++){double a=(Math.PI*2/12)*s;if(t%4==0){for(double d=0.5;d<sL;d+=1.0)gf(w,l.clone().add(Math.cos(a)*d,0.1,Math.sin(a)*d),rm(bm),0,0,0,110+rng().nextInt(20));for(double d=0.5;d<sL*0.7;d+=1.2){double h=Math.max(0.3,(sL-d)*0.5+rng().nextDouble(0.3));if(h>3.5)h=3.5;gf(w,l.clone().add(Math.cos(a)*d,h,Math.sin(a)*d),rm(bm),0,0,0,110+rng().nextInt(20));if(h>1.2)gf(w,l.clone().add(Math.cos(a)*d,h*0.5,Math.sin(a)*d),rm(bm),0,0,0,110+rng().nextInt(20));}}for(double d=0.3;d<sL;d+=0.4){double h=0.2+Math.max(0,(sL-d)*0.4);w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*d,h,Math.sin(a)*d),2,0.04,0.05,0.04,0,new Particle.DustOptions(Color.fromRGB(180,230,255),1.7f));}}if(t%6==0){double pH=t*0.05;if(pH>4)pH=4;for(double y=0;y<pH;y+=0.8)gf(w,l.clone().add(0,y,0),rm(bm),0,0,0,100+rng().nextInt(20));}w.spawnParticle(Particle.SNOWFLAKE,l.clone().add(0,1,0),18,0.4,1.5,0.4,0.02);w.spawnParticle(Particle.DUST,l.clone().add(0,0.5,0),12,0.3,1.0,0.3,0,new Particle.DustOptions(Color.fromRGB(200,240,255),2.0f));if(t%6==0)w.playSound(l,Sound.ENTITY_PLAYER_HURT_FREEZE,0.5f,1.0f+t*0.004f);if(t%10==0)w.playSound(l,Sound.BLOCK_GLASS_BREAK,0.4f,1.5f);t+=2;}}.runTaskTimer(plugin,25,2);
        // Blizzard
        new BukkitRunnable(){int t=0;public void run(){if(t>=110||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}for(int arm=0;arm<6;arm++){double a=t*0.35+arm*Math.PI*2/6;double r=1.5;for(double y=0;y<3;y+=0.4)w.spawnParticle(Particle.SNOWFLAKE,l.clone().add(Math.cos(a+y*0.4)*r,y,Math.sin(a+y*0.4)*r),3,0.05,0.05,0.05,0.01);}if(t%12==0)for(int b=0;b<2;b++)gf(w,l.clone().add(rng().nextDouble(-1.5,1.5),rng().nextDouble(0.5,3),rng().nextDouble(-1.5,1.5)),rm(bm),rng().nextDouble(-0.02,0.02),0.03,rng().nextDouble(-0.02,0.02),40);t+=2;}}.runTaskTimer(plugin,50,2);
        // Spike explosion — 60 blocks in 12 directions
        new BukkitRunnable(){public void run(){Location l=vLoc(victim);if(l==null)return;w.spawnParticle(Particle.SNOWFLAKE,l,400,7,3,7,0.2);w.spawnParticle(Particle.DUST,l,400,7,3,7,0,new Particle.DustOptions(Color.fromRGB(150,220,255),3.5f));w.spawnParticle(Particle.DUST,l,250,6,2,6,0,new Particle.DustOptions(Color.fromRGB(80,180,255),2.8f));w.spawnParticle(Particle.EXPLOSION_EMITTER,l,5,2,0.5,2,0);w.spawnParticle(Particle.END_ROD,l,80,5,1.5,5,0.12);w.playSound(l,Sound.BLOCK_GLASS_BREAK,2.0f,0.4f);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,1.5f,1.5f);w.playSound(l,Sound.ENTITY_PLAYER_HURT_FREEZE,1.5f,0.3f);for(int s=0;s<12;s++){double a=(Math.PI*2/12)*s;for(int b=0;b<5;b++){double sp=0.7+rng().nextDouble(1.2);ge(w,l.clone().add(Math.cos(a)*0.5,0.2+b*0.35,Math.sin(a)*0.5),rm(bm),Math.cos(a)*sp,rng().nextDouble(0.03,0.25),Math.sin(a)*sp,44+rng().nextInt(22));}}}}.runTaskLater(plugin,163);
        return D;
    }

    // === 8. DRAGON — SKY BLOCK TORNADO (sky, 240t) ===
    private int playDragon(Player victim) {
        final int D=240; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,200,2,false,false,false));w.playSound(o,Sound.ENTITY_ENDER_DRAGON_GROWL,1.5f,0.5f);
        Material[] bm={Material.END_STONE,Material.END_STONE_BRICKS,Material.PURPUR_BLOCK,Material.PURPUR_PILLAR,Material.PURPLE_CONCRETE,Material.OBSIDIAN,Material.CRYING_OBSIDIAN};
        new BukkitRunnable(){int t=0;public void run(){if(t>=70||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}double r=3.5-t*0.03;for(int i=0;i<20;i++){double a=(Math.PI*2/20)*i+t*0.08;w.spawnParticle(Particle.DRAGON_BREATH,l.clone().add(Math.cos(a)*r,0.2,Math.sin(a)*r),4,0.05,0.06,0.05,0.004);w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*r*0.6,0.3,Math.sin(a)*r*0.6),3,0.06,0.03,0.06,0,new Particle.DustOptions(Color.fromRGB(120,0,180),2.0f));}if(t%6==0)for(int b=0;b<4;b++){double a=rng().nextDouble(Math.PI*2);double r2=1+rng().nextDouble(2.5);gb(w,l.clone().add(Math.cos(a)*r2,-0.5,Math.sin(a)*r2),rm(bm),0,rng().nextDouble(0.3,0.7),0,65);}if(t%10==0)w.playSound(l,Sound.ENTITY_ENDER_DRAGON_FLAP,0.6f,0.5f);t+=2;}}.runTaskTimer(plugin,0,2);
        new BukkitRunnable(){int t=0;public void run(){if(t>=140||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}for(int arm=0;arm<4;arm++){double a=t*0.35+arm*Math.PI/2;double r=2.2-t*0.008;if(r<0.5)r=0.5;for(double y=0;y<4;y+=0.35)w.spawnParticle(Particle.DRAGON_BREATH,l.clone().add(Math.cos(a+y*0.6)*r,y-0.5,Math.sin(a+y*0.6)*r),4,0.04,0.05,0.04,0.003);}w.spawnParticle(Particle.DUST,l,14,0.5,1.5,0.5,0,new Particle.DustOptions(Color.fromRGB(150,0,220),2.2f));w.spawnParticle(Particle.END_ROD,l.clone().add(0,1,0),5,0.3,0.6,0.3,0.02);if(t%6==0)for(int b=0;b<3;b++){double a=t*0.4+b*Math.PI*2/3;double r=1.5+Math.sin(t*0.1)*0.5;gb(w,l.clone().add(Math.cos(a)*r,-1,Math.sin(a)*r),rm(bm),-Math.cos(a)*0.05,rng().nextDouble(0.3,0.8),-Math.sin(a)*0.05,45);}if(t%14==0){w.playSound(l,Sound.ENTITY_ENDER_DRAGON_GROWL,0.4f,0.8f+t*0.005f);w.spawnParticle(Particle.SONIC_BOOM,l,1,0,0,0,0);}t+=2;}}.runTaskTimer(plugin,50,2);
        new BukkitRunnable(){public void run(){if(!victim.isOnline())return;victim.removePotionEffect(PotionEffectType.LEVITATION);victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,100,4,false,false,false));}}.runTaskLater(plugin,100);
        new BukkitRunnable(){public void run(){Location l=vLoc(victim);if(l==null)return;w.spawnParticle(Particle.DRAGON_BREATH,l,500,7,7,7,0.15);w.spawnParticle(Particle.DUST,l,350,6,6,6,0,new Particle.DustOptions(Color.fromRGB(150,0,220),3.5f));w.spawnParticle(Particle.DUST,l,250,5,5,5,0,new Particle.DustOptions(Color.fromRGB(200,100,255),2.8f));w.spawnParticle(Particle.END_ROD,l,150,5,5,5,0.25);w.spawnParticle(Particle.EXPLOSION_EMITTER,l,8,2.5,2.5,2.5,0);w.spawnParticle(Particle.SONIC_BOOM,l,4,1.5,1.5,1.5,0);w.playSound(l,Sound.ENTITY_ENDER_DRAGON_DEATH,1.5f,0.8f);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,2.0f,0.4f);for(int b=0;b<55;b++)ge(w,l.clone().add(0,1,0),rm(bm),rng().nextDouble(-2.2,2.2),rng().nextDouble(0.5,2.8),rng().nextDouble(-2.2,2.2),52+rng().nextInt(25));}}.runTaskLater(plugin,200);
        new BukkitRunnable(){int t=0;public void run(){if(t>=30){cancel();return;}Location l=vLoc(victim);if(l==null)l=o.clone().add(0,5,0);w.spawnParticle(Particle.DRAGON_BREATH,l,25-t,4,4,4,0.03);w.spawnParticle(Particle.END_ROD,l,6,2.5,2.5,2.5,0.02);if(t%8==0)w.playSound(l,Sound.ENTITY_ENDER_DRAGON_FLAP,0.4f,1.2f);t+=3;}}.runTaskTimer(plugin,205,3);
        return D;
    }

    // === 9. SOUL VORTEX — EXTREME SPIN (spin, 200t) ===
    private int playSoulVortex(Player victim) {
        final int D=200; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        w.playSound(o,Sound.ENTITY_WARDEN_EMERGE,1.0f,0.5f);w.playSound(o,Sound.BLOCK_SCULK_CATALYST_BLOOM,1.0f,0.6f);
        Material[] bm={Material.SOUL_SAND,Material.SOUL_SOIL,Material.SOUL_LANTERN,Material.CYAN_CONCRETE,Material.LIGHT_BLUE_CONCRETE,Material.WARPED_PLANKS};
        new BukkitRunnable(){int t=0;public void run(){if(t>=35||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}double r=0.5+t*0.12;for(int i=0;i<24;i++){double a=(Math.PI*2/24)*i+t*0.12;w.spawnParticle(Particle.SOUL_FIRE_FLAME,l.clone().add(Math.cos(a)*r,0.08,Math.sin(a)*r),2,0.04,0.02,0.04,0.005);}w.spawnParticle(Particle.SOUL,l.clone().add(0,0.5,0),4,0.4,0.2,0.4,0.01);if(t%6==0)for(int b=0;b<3;b++){double a=rng().nextDouble(Math.PI*2);double d=rng().nextDouble(0.5,r);gf(w,l.clone().add(Math.cos(a)*d,-0.2,Math.sin(a)*d),rm(bm),0,0,0,80);}if(t%8==0)w.playSound(l,Sound.BLOCK_SOUL_SAND_STEP,0.8f,0.5f);t+=2;}}.runTaskTimer(plugin,0,2);
        new BukkitRunnable(){int t=0;float spin=8f;public void run(){if(t>=160||!victim.isOnline()){cancel();return;}Location l=victim.getLocation();spin=Math.min(spin+1.5f,120f);l.setYaw(l.getYaw()+spin);l.setPitch((float)(Math.sin(t*0.08)*45));victim.teleport(l);if(t%2==0){w.spawnParticle(Particle.SOUL_FIRE_FLAME,l.clone().add(0,1,0),6,0.5,0.6,0.5,0.025);w.spawnParticle(Particle.SOUL,l.clone().add(0,1.5,0),3,0.25,0.4,0.25,0.06);}t++;}}.runTaskTimer(plugin,20,1);
        new BukkitRunnable(){int t=0;public void run(){if(t>=145||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}for(int arm=0;arm<6;arm++){double a=t*0.6+arm*Math.PI/3;double maxR=3.0-t*0.015;if(maxR<0.3)maxR=0.3;for(double d=0.3;d<maxR;d+=0.35)w.spawnParticle(Particle.SOUL_FIRE_FLAME,l.clone().add(Math.cos(a+d*0.5)*d,0.1+d*0.15,Math.sin(a+d*0.5)*d),2,0.03,0.02,0.03,0.003);}if(t%4==0)w.spawnParticle(Particle.SOUL,l.clone().add(0,2.5,0),5,0.6,0.6,0.6,0.1);if(t%6==0)for(int b=0;b<5;b++){double a2=rng().nextDouble(Math.PI*2);double dist=3+rng().nextDouble(2.5);gb(w,l.clone().add(Math.cos(a2)*dist,0.3,Math.sin(a2)*dist),rm(bm),-Math.cos(a2)*0.22,rng().nextDouble(0.05,0.2),-Math.sin(a2)*0.22,35+rng().nextInt(12));}if(t%14==0)for(int b=0;b<2;b++)gf(w,l.clone().add(rng().nextDouble(-1.5,1.5),0.5,rng().nextDouble(-1.5,1.5)),Material.SOUL_LANTERN,0,0.06,0,50);if(t%16==0){w.playSound(l,Sound.ENTITY_WARDEN_HEARTBEAT,0.8f,0.6f);w.playSound(l,Sound.BLOCK_SCULK_SENSOR_CLICKING,0.5f,0.8f);}t+=2;}}.runTaskTimer(plugin,25,2);
        new BukkitRunnable(){public void run(){Location l=vLoc(victim);if(l==null)return;w.spawnParticle(Particle.SOUL_FIRE_FLAME,l,500,6,3,6,0.25);w.spawnParticle(Particle.SOUL,l,300,5,4,5,0.2);w.spawnParticle(Particle.EXPLOSION_EMITTER,l,6,2,0.5,2,0);w.spawnParticle(Particle.SONIC_BOOM,l,3,1,0.5,1,0);w.playSound(l,Sound.ENTITY_WARDEN_SONIC_BOOM,1.5f,0.5f);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,1.5f,0.5f);for(int b=0;b<50;b++){double a=(Math.PI*2/50)*b;double sp=0.6+rng().nextDouble(1.0);ge(w,l.clone().add(0,0.5,0),rm(bm),Math.cos(a)*sp,rng().nextDouble(0.1,0.5),Math.sin(a)*sp,48+rng().nextInt(22));}}}.runTaskLater(plugin,172);
        return D;
    }

    // === 10. WITHER STORM — DARK FORTRESS (ground, 200t) ===
    private int playWitherStorm(Player victim) {
        final int D=200; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        w.playSound(o,Sound.ENTITY_WITHER_SPAWN,1.0f,0.5f);
        Material[] bm={Material.COAL_BLOCK,Material.BLACK_CONCRETE,Material.BLACKSTONE,Material.POLISHED_BLACKSTONE_BRICKS,Material.OBSIDIAN,Material.GILDED_BLACKSTONE};
        new BukkitRunnable(){int t=0;public void run(){if(t>=65||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}double r=0.5+t*0.07;for(int i=0;i<24;i++){double a=(Math.PI*2/24)*i+t*0.1;w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*r,0.06,Math.sin(a)*r),3,0.05,0.01,0.05,0,new Particle.DustOptions(Color.fromRGB(15,15,15),2.5f));w.spawnParticle(Particle.SQUID_INK,l.clone().add(Math.cos(a)*r*0.5,0.12,Math.sin(a)*r*0.5),1,0.04,0.02,0.04,0.004);}if(t%4==0){double yy=(t/4)*0.5;if(yy>4)yy=4;for(int p=0;p<4;p++){double a=(Math.PI*2/4)*p+Math.PI/4;gf(w,l.clone().add(Math.cos(a)*2.5,yy,Math.sin(a)*2.5),rm(bm),0,0,0,120);double a2=a+0.3;gf(w,l.clone().add(Math.cos(a2)*2.5,yy,Math.sin(a2)*2.5),rm(bm),0,0,0,120);}}if(t%10==0)w.playSound(l,Sound.ENTITY_WITHER_AMBIENT,0.6f,0.5f);t+=2;}}.runTaskTimer(plugin,0,2);
        new BukkitRunnable(){int t=0;public void run(){if(t>=125||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}for(int s=0;s<3;s++){double a=t*0.4+s*Math.PI*2/3;double r=2.8-t*0.012;if(r<1)r=1;Location sk=l.clone().add(Math.cos(a)*r,1.2,Math.sin(a)*r);w.spawnParticle(Particle.DUST,sk,10,0.15,0.15,0.15,0,new Particle.DustOptions(Color.BLACK,2.2f));w.spawnParticle(Particle.SQUID_INK,sk,4,0.1,0.1,0.1,0.012);}w.spawnParticle(Particle.SQUID_INK,l.clone().add(0,0.5,0),10,0.2,0.5,0.2,0.015);w.spawnParticle(Particle.DUST,l.clone().add(0,0.3,0),12,0.4,0.8,0.4,0,new Particle.DustOptions(Color.fromRGB(30,0,30),2.2f));if(t%10==0)for(int b=0;b<3;b++){double a=rng().nextDouble(Math.PI*2);double dist=1+rng().nextDouble(2);gb(w,l.clone().add(Math.cos(a)*dist,-0.3,Math.sin(a)*dist),rm(bm),rng().nextDouble(-0.08,0.08),rng().nextDouble(0.25,0.7),rng().nextDouble(-0.08,0.08),50);}if(t%16==0)w.playSound(l,Sound.ENTITY_WITHER_SHOOT,0.5f,0.5f);t+=2;}}.runTaskTimer(plugin,40,2);
        new BukkitRunnable(){public void run(){Location l=vLoc(victim);if(l==null)return;w.spawnParticle(Particle.SQUID_INK,l,500,6,3,6,0.25);w.spawnParticle(Particle.DUST,l,350,6,3,6,0,new Particle.DustOptions(Color.BLACK,3.5f));w.spawnParticle(Particle.DUST,l,250,5,2,5,0,new Particle.DustOptions(Color.fromRGB(50,0,50),2.8f));w.spawnParticle(Particle.EXPLOSION_EMITTER,l,8,2,0.5,2,0);w.spawnParticle(Particle.SONIC_BOOM,l,3,1.5,0.5,1.5,0);w.playSound(l,Sound.ENTITY_WITHER_DEATH,1.5f,0.5f);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,2.0f,0.3f);for(int b=0;b<55;b++){double a=(Math.PI*2/55)*b;double sp=0.6+rng().nextDouble(1.0);ge(w,l.clone().add(0,0.5,0),rm(bm),Math.cos(a)*sp,rng().nextDouble(0.15,0.7),Math.sin(a)*sp,48+rng().nextInt(25));}}}.runTaskLater(plugin,168);
        new BukkitRunnable(){int t=0;public void run(){if(t>=25){cancel();return;}Location l=vLoc(victim);if(l==null)l=o;w.spawnParticle(Particle.SQUID_INK,l,25-t,4,1,4,0.03);if(t%8==0)w.playSound(l,Sound.ENTITY_WITHER_AMBIENT,0.3f,0.8f);t+=3;}}.runTaskTimer(plugin,173,3);
        return D;
    }

    // === 11. SCULK — SONIC TENDRILS (ground, 200t) ===
    private int playSculkResonance(Player victim) {
        final int D=200; Location o=victim.getLocation().clone(); World w=o.getWorld(); if(w==null)return 20;
        w.playSound(o,Sound.BLOCK_SCULK_CATALYST_BLOOM,1.2f,0.3f);w.playSound(o,Sound.ENTITY_WARDEN_EMERGE,0.8f,0.8f);
        Material[] bm={Material.SCULK,Material.SCULK_CATALYST,Material.SCULK_VEIN,Material.CYAN_CONCRETE,Material.DARK_PRISMARINE,Material.PRISMARINE_BRICKS};
        new BukkitRunnable(){int t=0;public void run(){if(t>=75||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}double len=0.5+t*0.08;for(int td=0;td<8;td++){double a=(Math.PI*2/8)*td+Math.sin(t*0.04)*0.15;for(double d=0;d<len;d+=0.35)w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*d,0.05,Math.sin(a)*d),2,0.04,0.01,0.04,0,new Particle.DustOptions(Color.fromRGB(0,50,60),2.3f));if(t%6==0){double tip=len-rng().nextDouble(0.5);if(tip<0.3)tip=0.3;gf(w,l.clone().add(Math.cos(a)*tip,-0.1,Math.sin(a)*tip),rm(bm),0,0,0,100+rng().nextInt(20));if(tip>1.5)gf(w,l.clone().add(Math.cos(a)*(tip-0.8),-0.1,Math.sin(a)*(tip-0.8)),rm(bm),0,0,0,100+rng().nextInt(20));}}if(t%10==0)w.playSound(l,Sound.BLOCK_SCULK_SENSOR_CLICKING,0.8f,0.5f+t*0.01f);t+=2;}}.runTaskTimer(plugin,0,2);
        int[] pulses={35,55,75,95,115,135,150};
        for(int i=0;i<pulses.length;i++){final int idx=i;new BukkitRunnable(){public void run(){Location l=vLoc(victim);if(l==null)return;w.spawnParticle(Particle.SONIC_BOOM,l.clone().add(0,0.5,0),1,0,0,0,0);w.playSound(l,Sound.ENTITY_WARDEN_SONIC_BOOM,0.5f+idx*0.12f,0.4f+idx*0.1f);double ringR=1.5+idx*0.9;for(int p=0;p<28;p++){double a=(Math.PI*2/28)*p;w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*ringR,0.3,Math.sin(a)*ringR),3,0.06,0.04,0.06,0,new Particle.DustOptions(Color.fromRGB(0,80,100),2.2f));}for(int b=0;b<5+idx;b++){double a=rng().nextDouble(Math.PI*2);gb(w,l.clone().add(Math.cos(a)*ringR*0.5,0.3,Math.sin(a)*ringR*0.5),rm(bm),Math.cos(a)*0.35,rng().nextDouble(0.2,0.7),Math.sin(a)*0.35,42);}}}.runTaskLater(plugin,pulses[idx]);}
        new BukkitRunnable(){int t=0;public void run(){if(t>=120||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}w.spawnParticle(Particle.DUST,l.clone().add(0,0.5,0),12,0.6,0.5,0.6,0,new Particle.DustOptions(Color.fromRGB(0,60,70),1.8f));w.spawnParticle(Particle.SCULK_CHARGE_POP,l.clone().add(0,0.3,0),6,1.0,0.3,1.0,0.01);if(t%6==0)w.playSound(l,Sound.BLOCK_SCULK_SENSOR_CLICKING,0.4f,1.0f);t+=2;}}.runTaskTimer(plugin,45,2);
        new BukkitRunnable(){public void run(){Location l=vLoc(victim);if(l==null)return;w.spawnParticle(Particle.SONIC_BOOM,l.clone().add(0,0.5,0),6,1.5,0.5,1.5,0);w.spawnParticle(Particle.SCULK_CHARGE_POP,l,400,6,1.5,6,0.12);w.spawnParticle(Particle.DUST,l,500,6,2,6,0,new Particle.DustOptions(Color.fromRGB(0,80,100),3.5f));w.spawnParticle(Particle.EXPLOSION_EMITTER,l,5,1.5,0.3,1.5,0);w.playSound(l,Sound.ENTITY_WARDEN_SONIC_BOOM,2.0f,0.3f);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,1.5f,0.5f);w.playSound(l,Sound.BLOCK_SCULK_CATALYST_BLOOM,1.5f,0.4f);for(int b=0;b<55;b++){double a=(Math.PI*2/55)*b;double sp=0.7+rng().nextDouble(1.1);ge(w,l.clone().add(0,0.3,0),rm(bm),Math.cos(a)*sp,rng().nextDouble(0.1,0.45),Math.sin(a)*sp,48+rng().nextInt(22));}}}.runTaskLater(plugin,168);
        new BukkitRunnable(){int t=0;public void run(){if(t>=25){cancel();return;}Location l=vLoc(victim);if(l==null)l=o;w.spawnParticle(Particle.SCULK_CHARGE_POP,l,18-t,4,0.5,4,0.02);if(t%8==0)w.playSound(l,Sound.BLOCK_SCULK_SENSOR_CLICKING,0.3f,1.5f);t+=3;}}.runTaskTimer(plugin,173,3);
        return D;
    }

    // === TOTEM ===
    public void playTotemExplosion(Location loc) { World w=loc.getWorld();if(w==null)return;w.spawnParticle(Particle.EXPLOSION_EMITTER,loc.clone().add(0,1,0),1,0,0,0,0);w.spawnParticle(Particle.TOTEM_OF_UNDYING,loc.clone().add(0,1,0),100,1,1,1,0.5);w.spawnParticle(Particle.FLASH,loc.clone().add(0,1,0),3,0,0,0,0);w.playSound(loc,Sound.ENTITY_GENERIC_EXPLODE,1.0f,1.5f);w.playSound(loc,Sound.ITEM_TOTEM_USE,1.0f,1.0f); }
    public int playTotemCounter(Player victim, Player killer) {
        final int D=100;Location o=victim.getLocation().clone();World w=o.getWorld();if(w==null)return 20;
        w.spawnParticle(Particle.TOTEM_OF_UNDYING,o.clone().add(0,1,0),150,1,1.5,1,0.5);w.spawnParticle(Particle.FLASH,o.clone().add(0,1,0),3,0,0,0,0);w.playSound(o,Sound.ITEM_TOTEM_USE,1.2f,1.0f);
        new BukkitRunnable(){int t=0;public void run(){if(t>=20||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}double r=1.2+t*0.05;for(int i=0;i<16;i++){double a=(Math.PI*2/16)*i+t*0.2;w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*r,0.5+t*0.1,Math.sin(a)*r),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(255,223,50),1.5f));}if(t%5==0)w.playSound(l,Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.5f,1.5f);t+=2;}}.runTaskTimer(plugin,15,2);
        new BukkitRunnable(){int t=0;public void run(){if(t>=30||!victim.isOnline()||!killer.isOnline()){cancel();return;}Location vl=victim.getLocation();Location kl=killer.getLocation();Vector dir=vl.toVector().subtract(kl.toVector()).normalize();for(double d=0;d<vl.distance(kl)&&d<6;d+=1.0){Location pt=kl.clone().add(dir.clone().multiply(d)).add(0,1,0);w.spawnParticle(Particle.DUST,pt,2,0.1,0.1,0.1,0,new Particle.DustOptions(Color.RED,1.5f));}Vector rev=kl.toVector().subtract(vl.toVector()).normalize();for(double d=0;d<3;d+=0.8){Location pt=vl.clone().add(rev.clone().multiply(d)).add(0,1,0);w.spawnParticle(Particle.DUST,pt,2,0.1,0.1,0.1,0,new Particle.DustOptions(Color.fromRGB(255,215,0),1.5f));}Location mid=vl.clone().add(kl).multiply(0.5).add(0,1,0);w.spawnParticle(Particle.ELECTRIC_SPARK,mid,10,0.3,0.3,0.3,0.1);if(t%6==0)w.playSound(mid,Sound.ENTITY_WARDEN_SONIC_BOOM,0.5f,1.5f);t+=3;}}.runTaskTimer(plugin,35,3);
        new BukkitRunnable(){public void run(){Location l=vLoc(victim);if(l==null)return;w.spawnParticle(Particle.TOTEM_OF_UNDYING,l.clone().add(0,1,0),200,3,2,3,0.3);w.spawnParticle(Particle.FLASH,l.clone().add(0,1,0),2,0,0,0,0);w.playSound(l,Sound.ENTITY_GENERIC_EXPLODE,1.2f,1.2f);for(int i=0;i<32;i++){double a=(Math.PI*2/32)*i;w.spawnParticle(Particle.END_ROD,l.clone().add(Math.cos(a)*2,0.5,Math.sin(a)*2),2,0,0,0,0.1);}}}.runTaskLater(plugin,65);
        new BukkitRunnable(){int t=0;public void run(){if(t>=15||!victim.isOnline()){cancel();return;}Location l=vLoc(victim);if(l==null){cancel();return;}w.spawnParticle(Particle.HEART,l.clone().add(0,2,0),2,0.3,0.2,0.3,0);w.spawnParticle(Particle.END_ROD,l.clone().add(0,1,0),3,0.5,1,0.5,0.05);t+=3;}}.runTaskTimer(plugin,85,3);
        return D;
    }
}
