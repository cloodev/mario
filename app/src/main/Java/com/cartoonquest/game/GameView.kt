package com.cartoonquest.game

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.MediaPlayer
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

private data class Platform(val x: Float, val y: Float, val w: Float, val h: Float = 22f)
private data class Enemy(var x: Float, var y: Float, val minX: Float, val maxX: Float, var dir: Float = 1f, val flying: Boolean = false)
private data class Coin(val x: Float, val y: Float, var taken: Boolean = false)
private data class Hazard(val x: Float, val y: Float, val w: Float)
private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
private data class World(val name: String, val sky: Int, val ground: Int, val accent: Int, val enemy: String)

class GameView(ctx: Context) : View(ctx) {
    private val prefs = ctx.getSharedPreferences("cartoonquest", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val worlds = listOf(
        World("Green Forest",0xFF83DDF5.toInt(),0xFF62C75B.toInt(),0xFFFFD34E.toInt(),"Slime"),
        World("Mushroom Hills",0xFFFFC6DA.toInt(),0xFF79C96B.toInt(),0xFFFF7A9D.toInt(),"Mushroom"),
        World("Desert Adventure",0xFFFFD18A.toInt(),0xFFE5B34D.toInt(),0xFFEA7945.toInt(),"Cactus"),
        World("Frozen Land",0xFF9DE7F4.toInt(),0xFFBCEBFA.toInt(),0xFF63B8E8.toInt(),"Snowling"),
        World("Lava Kingdom",0xFFFFA16E.toInt(),0xFF6D4652.toInt(),0xFFFF663F.toInt(),"Firebug"),
        World("Candy World",0xFFFFBCE8.toInt(),0xFFB9E78A.toInt(),0xFFFF69B4.toInt(),"Candy"),
        World("Sky Islands",0xFF83C8FF.toInt(),0xFFB3DDF2.toInt(),0xFFFFE17A.toInt(),"Cloudling"),
        World("Haunted Castle",0xFF9C9ACB.toInt(),0xFF686080.toInt(),0xFFB4A4FF.toInt(),"Ghost"),
        World("Ocean World",0xFF67D6E9.toInt(),0xFF4C9F9C.toInt(),0xFFFFD66B.toInt(),"Crab"),
        World("Crystal Cave",0xFF6F91C8.toInt(),0xFF596C9E.toInt(),0xFF8CF2E6.toInt(),"Bat"),
        World("Dragon Valley",0xFFFFB17A.toInt(),0xFF9B7657.toInt(),0xFFFFD05A.toInt(),"Dragon"),
        World("Dark Kingdom",0xFF4B527D.toInt(),0xFF41445D.toInt(),0xFFFF83A6.toInt(),"Shadow")
    )
    private var screen = 0 // 0 lobby, 1 world select, 2 level select, 3 game, 4 win
    private var world = 0
    private var level = 1
    private var unlocked = prefs.getInt("unlocked",1)
    private var bestCoins = prefs.getInt("coins",0)
    private var coinsNow = 0
    private var playerX = 90f; private var playerY = 0f; private var vx = 0f; private var vy = 0f
    private var onGround = false; private var facing = 1f; private var camera = 0f
    private var left = false; private var right = false; private var jumpQueued = false
    private var time = 0f; private var hp = 3; private var checkpoint = 80f
    private var bossX = -1f; private var bossHp = 0; private var bossFlash = 0f
    private val particles = mutableListOf<Particle>()
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
    private var music: MediaPlayer? = null
    private var paused = false
    private var leftPointer = -1; private var rightPointer = -1; private var jumpPointer = -1
    private var platforms = mutableListOf<Platform>(); private var enemies = mutableListOf<Enemy>()
    private var coins = mutableListOf<Coin>(); private var hazards = mutableListOf<Hazard>()
    private var goalX = 0f; private var worldWidth = 2200f
    private var last = System.nanoTime()
    private val w get() = width.toFloat()
    private val h get() = height.toFloat()
    private val groundY get() = h * .78f
    private val unlockedWorld get() = ((unlocked - 1) / 20).coerceIn(0,11)
    private val unlockedLevel get() = ((unlocked - 1) % 20 + 1).coerceIn(1,20)

    init {
        isFocusable = true
        try {
            music = MediaPlayer()
            val afd = context.assets.openFd("music_loop.wav")
            music?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            music?.isLooping = true
            music?.setVolume(.20f, .20f)
            music?.prepare()
            music?.start()
        } catch (_: Exception) { music = null }
    }

    override fun onDetachedFromWindow() {
        try { music?.stop(); music?.release() } catch (_: Exception) {}
        tone.release()
        super.onDetachedFromWindow()
    }
    private fun sfx(freq: Int, duration: Int = 90) {
        try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, duration) } catch (_: Exception) {}
    }
    private fun burst(x: Float, y: Float, count: Int, c: Int) {
        repeat(count) {
            val a = Random.nextFloat() * (Math.PI * 2).toFloat()
            val sp = 65f + Random.nextFloat() * 180f
            particles.add(Particle(x, y, cos(a)*sp, sin(a)*sp-55f, .55f, c))
        }
    }

    private fun color(c:Int) { paint.color=c; paint.style=Paint.Style.FILL }
    private fun rect(c:Int,x:Float,y:Float,ww:Float,hh:Float,r:Float=0f) {
        color(c); canvasRef?.drawRoundRect(x,y,x+ww,y+hh,r,r,paint)
    }
    private var canvasRef: Canvas? = null
    private fun circle(c:Int,x:Float,y:Float,r:Float) { color(c); canvasRef?.drawCircle(x,y,r,paint) }
    private fun text(s:String,x:Float,y:Float,size:Float,c:Int=Color.WHITE,bold:Boolean=true) {
        color(c); paint.textSize=size; paint.typeface=if(bold) Typeface.create("sans-serif-rounded",Typeface.BOLD) else Typeface.create("sans-serif",Typeface.NORMAL)
        canvasRef?.drawText(s,x,y,paint)
    }
    private fun button(label:String,x:Float,y:Float,bw:Float,bh:Float,fill:Int=0xFF263D5C.toInt()) {
        rect(0x55303A55,x+3,y+5,bw,bh,18f); rect(fill,x,y,bw,bh,18f)
        text(label,x+18,y+bh*.66f,bh*.34f)
    }

    override fun onDraw(c:Canvas) {
        super.onDraw(c); canvasRef=c
        val now=System.nanoTime(); val dt=((now-last)/1e9).toFloat().coerceIn(0f,.04f); last=now; time+=dt
        when(screen) {
            0 -> drawLobby()
            1 -> drawWorlds()
            2 -> drawLevels()
            3 -> { if (!paused) update(dt); drawGame(); if (paused) drawPause() }
            4 -> drawWin()
        }
        canvasRef=null
        postInvalidateOnAnimation()
    }

    private fun background(sky:Int) {
        color(sky); canvasRef?.drawRect(0f,0f,w,h,paint)
        for(i in 0..5) {
            val x=((i*240f - (if(screen==3) camera*.18f else 0f)) % (w+260f)+w+260f)%(w+260f)-100f
            circle(0x55FFFFFF,x+35, h*.19f+sin(time+i)*5,27f)
            circle(0x55FFFFFF,x+62,h*.18f+sin(time+i)*5,35f)
            circle(0x55FFFFFF,x+90,h*.2f+sin(time+i)*5,25f)
        }
        for(i in 0..5) {
            val x=i*w/4f-(if(screen==3)camera*.35f else 0f)
            color(0x553A9C78); val p=Path(); p.moveTo(x-100,h*.72f); p.quadTo(x+70,h*.28f,x+250,h*.72f); p.close(); canvasRef?.drawPath(p,paint)
        }
    }

    private fun drawLobby() {
        background(worlds[0].sky)
        text("CARTOON",w*.09f,h*.22f,h*.12f,0xFF23405C.toInt())
        text("QUEST",w*.09f,h*.34f,h*.12f,0xFFFF8A45.toInt())
        text("A LITTLE HERO, A BIG ADVENTURE!",w*.1f,h*.43f,h*.035f,0xFF31516A.toInt(),false)
        drawHero(w*.72f,h*.58f,1f,0f)
        button("PLAY",w*.1f,h*.57f,w*.25f,h*.13f,0xFFFF9D43.toInt())
        button("WORLDS",w*.1f,h*.74f,w*.25f,h*.10f)
        rect(0xAAFFFFFF,w*.72f,h*.08f,w*.2f,h*.1f,18f)
        text("🪙 $bestCoins",w*.75f,h*.145f,h*.045f,0xFF28445E.toInt())
        text("12 WORLDS  •  240 LEVELS",w*.09f,h*.91f,h*.035f,0xFF31516A.toInt(),false)
    }

    private fun drawWorlds() {
        background(0xFF9BDDF2.toInt()); text("CHOOSE YOUR WORLD",w*.08f,h*.15f,h*.075f,0xFF25445E.toInt())
        val cols=3; val bw=w*.26f; val bh=h*.22f
        for(i in 0 until 12) {
            val x=w*.07f+(i%cols)*(bw+w*.035f); val y=h*.23f+(i/cols)*(bh+h*.045f)
            val locked=i>unlockedWorld
            rect(if(locked)0xFF8D9AA8.toInt() else worlds[i].accent,x,y,bw,bh,20f)
            text(if(locked)"🔒" else "★",x+12,y+bh*.42f,bh*.35f)
            text(worlds[i].name,x+12,y+bh*.72f,bh*.19f,0xFF263B50.toInt())
            text(if(locked)"LOCKED" else "20 LEVELS",x+12,y+bh*.9f,bh*.13f,0xFF263B50.toInt(),false)
        }
        button("BACK",w*.04f,h*.05f,w*.14f,h*.09f)
    }

    private fun drawLevels() {
        background(worlds[world].sky); text(worlds[world].name.uppercase(),w*.08f,h*.16f,h*.07f,0xFF25445E.toInt())
        text("COMPLETE ALL 20 LEVELS TO UNLOCK THE NEXT WORLD",w*.08f,h*.23f,h*.03f,0xFF31516A.toInt(),false)
        for(i in 1..20) {
            val col=(i-1)%5; val row=(i-1)/5; val x=w*.08f+col*w*.17f; val y=h*.31f+row*h*.14f
            val global=world*20+i; val locked=global>unlocked
            rect(if(locked)0xFF8795A5.toInt() else 0xFFFFB84D.toInt(),x,y,w*.13f,h*.105f,15f)
            text(if(locked)"🔒" else "$i",x+w*.045f,y+h*.068f,h*.045f,0xFF263B50.toInt())
        }
        button("BACK",w*.04f,h*.05f,w*.14f,h*.09f)
    }

    private fun makeLevel() {
        platforms.clear(); enemies.clear(); coins.clear(); hazards.clear()
        worldWidth=1900f+level*45f
        platforms.add(Platform(0f,groundY,w*.7f,30f))
        var x=260f
        val rnd=Random(world*1000+level*91)
        while(x<worldWidth-260) {
            val gap=if(level%4==0) rnd.nextInt(80,145).toFloat() else rnd.nextInt(30,85).toFloat()
            val pw=rnd.nextInt(115,230).toFloat()
            val py=groundY-rnd.nextInt(65,190)
            platforms.add(Platform(x,py,pw,22f))
            coins.add(Coin(x+pw*.5f,py-45f)); coins.add(Coin(x+pw*.5f+28,py-45f))
            if(rnd.nextFloat()<.72f) enemies.add(Enemy(x+35,py-30,x+8,x+pw-30,1f,level%3==0))
            if(rnd.nextFloat()<.48f) hazards.add(Hazard(x+pw+gap*.15f,groundY-25,35f))
            x+=pw+gap
            if(x<worldWidth-300) platforms.add(Platform(x-gap,groundY, gap+20,30f))
        }
        platforms.add(Platform(worldWidth-260,groundY,300f,30f))
        goalX=worldWidth-150
        bossX = if (level % 5 == 0) worldWidth-390f else -1f
        bossHp = if (bossX > 0f) 4 else 0
        bossFlash = 0f
        particles.clear()
        for(i in 0..5) coins.add(Coin(450f+i*230f,groundY-85f))
        if(level%5==0) enemies.add(Enemy(worldWidth-430,groundY-55,worldWidth-550,worldWidth-240,1.2f,true))
        playerX=80f; playerY=groundY-52; vx=0f; vy=0f; camera=0f; hp=3; coinsNow=0; checkpoint=80f
    }

    private fun update(dt:Float) {
        if(platforms.isEmpty()) makeLevel()
        val accel=1500f
        if(left) { vx-=accel*dt; facing=-1f }
        if(right) { vx+=accel*dt; facing=1f }
        if(!left&&!right) vx*=.82f
        vx=vx.coerceIn(-300f,300f)
        if(jumpQueued&&onGround) { vy=-620f; onGround=false; sfx(620,65); burst(playerX+20,playerY+48,5,0xFFFFFFFF.toInt()) }
        jumpQueued=false
        vy=(vy+1550f*dt).coerceAtMost(850f)
        val oldY=playerY
        playerX+=vx*dt; playerY+=vy*dt
        playerX=playerX.coerceIn(0f,worldWidth-45)
        onGround=false
        for(p in platforms) {
            if(playerX+34>p.x && playerX+4<p.x+p.w && oldY+48<=p.y+10 && playerY+48>=p.y && vy>=0) {
                playerY=p.y-48; vy=0f; onGround=true
            }
        }
        for(e in enemies) {
            e.x+=e.dir*(if(e.flying)95f else 65f)*dt
            if(e.x<e.minX||e.x>e.maxX) e.dir*=-1
            if(abs(playerX-e.x)<34&&abs(playerY-e.y)<36) {
                if(vy>80&&playerY<e.y) { e.x=-9999f; vy=-350f } else { hurt() }
            }
        }
        if (bossX > 0f && bossHp > 0) {
            bossX += (if (playerX < bossX) -1f else 1f) * 48f * dt
            bossX = bossX.coerceIn(worldWidth-570f, worldWidth-230f)
            bossFlash = (bossFlash-dt).coerceAtLeast(0f)
            if (abs(playerX-bossX)<48f && abs(playerY-(groundY-58f))<58f) {
                if (vy > 80f && playerY < groundY-75f && bossFlash<=0f) {
                    bossHp--; vy=-420f; bossFlash=.55f; burst(bossX,groundY-65f,16,0xFFFFD05A.toInt()); sfx(700,130)
                    if (bossHp<=0) { burst(bossX,groundY-65f,30,0xFFFF8A45.toInt()); bossX=-1f; sfx(900,220) }
                } else if (bossFlash<=0f) hurt()
            }
        }
        for(c in coins) if(!c.taken&&abs(playerX+20-c.x)<28&&abs(playerY+22-c.y)<35) {
            c.taken=true; coinsNow++; burst(c.x,c.y,7,0xFFFFD34E.toInt()); sfx(950,55)
        }
        for (p in particles) { p.x+=p.vx*dt; p.y+=p.vy*dt; p.vy+=360f*dt; p.life-=dt }
        particles.removeAll { it.life<=0f }
        for(z in hazards) if(playerX+32>z.x&&playerX<z.x+z.w&&playerY+45>z.y) hurt()
        if(playerY>h+150) hurt()
        if(playerX>goalX && bossHp<=0) { screen=4; unlocked=max(unlocked,world*20+level+1); prefs.edit().putInt("unlocked",unlocked).putInt("coins",bestCoins+coinsNow).apply(); bestCoins+=coinsNow }
        if(playerX>checkpoint+500) checkpoint=playerX
        camera=(playerX-w*.35f).coerceIn(0f,(worldWidth-w).coerceAtLeast(0f))
    }

    private fun hurt() {
        hp--
        if(hp<=0) { playerX=checkpoint; playerY=groundY-60; hp=3 } else { playerX=checkpoint; playerY=groundY-60 }
        vx=0f; vy=0f
    }

    private fun drawGame() {
        background(worlds[world].sky)
        canvasRef?.save(); canvasRef?.translate(-camera,0f)
        for(p in platforms) {
            rect(worlds[world].ground,p.x,p.y,p.w,p.h,8f)
            rect(0xFF3D9E55.toInt(),p.x,p.y,p.w,9f,7f)
            for(i in 0 until (p.w/45).toInt()) circle(0x5578E06A,p.x+i*45+12,p.y+3,3f)
        }
        for(z in hazards) {
            val path=Path(); path.moveTo(z.x,z.y+25); path.lineTo(z.x+z.w/2,z.y-12); path.lineTo(z.x+z.w,z.y+25); path.close()
            color(0xFFEA5362.toInt()); canvasRef?.drawPath(path,paint)
        }
        for(c in coins) if(!c.taken) {
            circle(0xFFFFA92F,c.x,c.y,12f); circle(0xFFFFE47A,c.x-3,c.y-3,4f)
        }
        for(e in enemies) if(e.x>-100) {
            circle(if(e.flying)0xFFB07BE7.toInt() else 0xFF65B96B.toInt(),e.x,e.y,19f)
            circle(Color.WHITE,e.x-7,e.y-4,5f); circle(Color.WHITE,e.x+7,e.y-4,5f)
            circle(Color.BLACK,e.x-6,e.y-4,2f); circle(Color.BLACK,e.x+8,e.y-4,2f)
            rect(0xFF47634C.toInt(),e.x-12,e.y+11,24f,7f,4f)
        }
        // floating particles
        for (p in particles) { paint.alpha=(255f*(p.life/.55f).coerceIn(0f,1f)).toInt(); color(p.color); canvasRef?.drawCircle(p.x,p.y,4f,paint); paint.alpha=255 }
        // boss encounter on every fifth level
        if (bossX > 0f && bossHp > 0) {
            val bc=if(bossFlash>0f) Color.WHITE else 0xFF9A4E65.toInt()
            circle(bc,bossX,groundY-58f,38f)
            circle(0xFFFFC36F.toInt(),bossX-13,groundY-65f,8f); circle(0xFFFFC36F.toInt(),bossX+13,groundY-65f,8f)
            circle(Color.BLACK,bossX-13,groundY-65f,3f); circle(Color.BLACK,bossX+13,groundY-65f,3f)
            rect(0xFF49384E.toInt(),bossX-24,groundY-35,48f,13f,6f)
            rect(0xFFE95D69.toInt(),bossX-35,groundY-112,70f,8f,4f)
            rect(0xFF75D47B.toInt(),bossX-35,groundY-112,70f*(bossHp/4f),8f,4f)
        }
        // finish flag
        rect(0xFFFAFAFA.toInt(),goalX,groundY-130,7f,130f,3f)
        val flag=Path(); flag.moveTo(goalX+7,groundY-128); flag.lineTo(goalX+75,groundY-105); flag.lineTo(goalX+7,groundY-82); flag.close()
        color(0xFFFFC447.toInt()); canvasRef?.drawPath(flag,paint)
        drawHero(playerX,playerY,1f,vx)
        canvasRef?.restore()
        rect(0xAA203650,w*.025f,h*.035f,w*.29f,h*.105f,16f)
        text("WORLD ${world+1}  •  LEVEL $level",w*.045f,h*.082f,h*.032f)
        text("♥ $hp     🪙 $coinsNow",w*.045f,h*.12f,h*.03f)
        if (bossHp>0) text("BOSS  $bossHp/4",w*.39f,h*.085f,h*.035f,0xFFFF5A57.toInt())
        // touch controls
        controlPad(w*.055f,h*.76f,"◀"); controlPad(w*.18f,h*.76f,"▶")
        controlPad(w*.78f,h*.73f,"JUMP",true)
        button("Ⅱ",w*.91f,h*.04f,w*.065f,h*.1f)
    }

    private fun drawHero(x:Float,y:Float,scale:Float,speed:Float) {
        val bob=if(abs(speed)>20) abs(sin(time*14))*3f else sin(time*2)*1.5f
        val xx=x; val yy=y+bob
        // shadow
        circle(0x33405263,xx+21,yy+49,19f)
        // shoes
        rect(0xFF5B4263.toInt(),xx+2,yy+38,17f,10f,5f); rect(0xFF5B4263.toInt(),xx+22,yy+38,17f,10f,5f)
        // body and head
        rect(0xFF3F88D8.toInt(),xx+5,yy+18,30f,25f,9f)
        circle(0xFFFFC18E.toInt(),xx+20,yy+13,17f)
        // hat
        rect(0xFFEF6657.toInt(),xx+3,yy-5,34f,9f,5f); rect(0xFFEF6657.toInt(),xx+10,yy-14,22f,13f,7f)
        circle(Color.WHITE,xx+14,yy+10,3.5f); circle(Color.WHITE,xx+25,yy+10,3.5f)
        circle(Color.BLACK,xx+15,yy+10,1.6f); circle(Color.BLACK,xx+26,yy+10,1.6f)
        rect(0xFFFFE3B6.toInt(),xx+19,yy+19,8f,3f,2f)
    }

    private fun controlPad(x:Float,y:Float,label:String,big:Boolean=false) {
        val r=if(big) h*.17f else h*.13f
        circle(0x66516A83,x+r,y+r,r); circle(0xCCFFFFFF,x+r,y+r,r*.82f)
        text(label,x+r*.42f,y+r*1.17f,r*.48f,0xFF25445E.toInt())
    }

    private fun drawPause() {
        rect(0xBB15243A.toInt(),w*.28f,h*.2f,w*.44f,h*.58f,24f)
        text("PAUSED",w*.42f,h*.36f,h*.08f)
        button("RESUME",w*.36f,h*.46f,w*.28f,h*.12f,0xFFFF9D43.toInt())
        button("LEVEL MAP",w*.36f,h*.62f,w*.28f,h*.1f)
    }

    private fun drawWin() {
        background(worlds[world].sky)
        text("LEVEL COMPLETE!",w*.25f,h*.28f,h*.1f,0xFF23405C.toInt())
        text("Great job, little hero!",w*.32f,h*.4f,h*.045f,0xFF31516A.toInt(),false)
        text("🪙 +$coinsNow COINS",w*.37f,h*.52f,h*.05f,0xFFFF9B35.toInt())
        button("NEXT LEVEL",w*.34f,h*.63f,w*.32f,h*.13f,0xFFFF9D43.toInt())
        button("LEVEL MAP",w*.04f,h*.05f,w*.18f,h*.09f)
    }

    override fun onTouchEvent(e:MotionEvent):Boolean {
        val idx=e.actionIndex
        val x=e.getX(idx); val y=e.getY(idx)
        if(screen==3) {
            if(e.actionMasked==MotionEvent.ACTION_DOWN || e.actionMasked==MotionEvent.ACTION_POINTER_DOWN) {
                if (paused) {
                    if (y in h*.43f..h*.61f) paused=false
                    else if (y>h*.60f) { paused=false; screen=2; platforms.clear() }
                    invalidate(); return true
                }
                if(x>w*.88f&&y<h*.2f) { paused=true; left=false; right=false; return true }
                if(x<w*.14f&&y>h*.68f) { leftPointer=e.getPointerId(idx); left=true }
                else if(x<w*.34f&&y>h*.68f) { rightPointer=e.getPointerId(idx); right=true }
                else if(x>w*.68f&&y>h*.6f) { jumpPointer=e.getPointerId(idx); jumpQueued=true }
            } else if(e.actionMasked==MotionEvent.ACTION_POINTER_UP || e.actionMasked==MotionEvent.ACTION_UP) {
                val id=e.getPointerId(idx)
                if(id==leftPointer) { leftPointer=-1; left=false }
                if(id==rightPointer) { rightPointer=-1; right=false }
                if(id==jumpPointer) jumpPointer=-1
            } else if(e.actionMasked==MotionEvent.ACTION_CANCEL) {
                left=false; right=false; leftPointer=-1; rightPointer=-1; jumpPointer=-1
            }
            return true
        }
        if(e.action!=MotionEvent.ACTION_UP) return true
        when(screen) {
            0 -> { if(y>h*.52f&&x<w*.5f) { world=unlockedWorld; level=unlockedLevel; screen=2 } else if(y>h*.68f&&x<w*.5f) screen=1 }
            1 -> {
                if(y<h*.16f&&x<w*.2f) screen=0
                else {
                    val col=((x-w*.07f)/(w*.295f)).toInt(); val row=((y-h*.23f)/(h*.265f)).toInt()
                    val idx2=row*3+col
                    if(idx2 in 0..unlockedWorld) { world=idx2; screen=2 }
                }
            }
            2 -> {
                if(y<h*.16f&&x<w*.2f) { screen=1; return true }
                val col=((x-w*.08f)/(w*.17f)).toInt(); val row=((y-h*.31f)/(h*.14f)).toInt()
                val idx2=row*5+col+1
                if(idx2 in 1..20 && world*20+idx2<=unlocked) { level=idx2; makeLevel(); screen=3 }
            }
            4 -> {
                if(y<h*.2f&&x<w*.25f) screen=2
                else { if(level<20) level++ else if(world<11) { world++; level=1 }; makeLevel(); screen=3 }
            }
        }
        invalidate(); return true
    }

}
