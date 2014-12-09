package com.github.bluenote

import org.lwjgl.input.Keyboard
import org.lwjgl.input.Keyboard._
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.ContextAttribs
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.DisplayMode
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GLContext
import org.lwjgl.opengl.PixelFormat

import com.oculusvr.capi.Hmd
import com.oculusvr.capi.OvrLibrary
import com.oculusvr.capi.OvrLibrary.ovrDistortionCaps._
import com.oculusvr.capi.OvrLibrary.ovrTrackingCaps._
import com.oculusvr.capi.OvrLibrary.ovrHmdCaps._
import com.oculusvr.capi.OvrVector2i
import com.oculusvr.capi.OvrVector3f
import com.oculusvr.capi.Posef
import com.oculusvr.capi.RenderAPIConfig
import com.oculusvr.capi.GLTexture
import com.sun.jna.Structure



object Main {

  /**
   * Initializes libOVR and returns the Hmd instance
   */
  def initHmd(): Hmd = {

    // OvrLibrary.INSTANCE.ovr_Initialize() // is this actually still needed?
    Hmd.initialize()
    
    val hmd = 
      //Hmd.createDebug(ovrHmd_DK1)
      Hmd.create(0)
    if (hmd == null) {
      println("Oculus Rift HMD not found.")
      System.exit(-1)
    }
    
    // set hmd caps
    val hmdCaps = ovrHmdCap_NoVSync | 
                  ovrHmdCap_LowPersistence | 
                  //ovrHmdCap_NoVSync |
                  //ovrHmdCap_ExtendDesktop | 
                  //ovrHmdCap_DynamicPrediction |
                  0
    hmd.setEnabledCaps(hmdCaps)
    
    hmd
  }
  
  
  /** Helper function used by initOpenGL */
  def setupContext(): ContextAttribs = {
    new ContextAttribs(3, 3)
    .withForwardCompatible(true)
    .withProfileCore(true)
    .withDebug(true)
  }
  
  /** Helper function used by initOpenGL */
 def setupDisplay(left: Int, top: Int, width: Int, height: Int) {
    Display.setDisplayMode(new DisplayMode(width, height));
    Display.setLocation(left, top)
    //Display.setLocation(0, 0)
    println(f"Creating window $width x $height @ x = $left, y = $top")
    //Display.setVSyncEnabled(true)
  }
 
  /**
   * Initializes OpenGL
   * I first ran into some issues with an "invalid memory access" in configureRendering 
   * depending on how I initialize OpenGL (probably a context issue, but this was with the old SDK). 
   * To solve the issue I now initialize OpenGL similar to LwjglApp.run. 
   */  
  def initOpenGL(hmd: Hmd) {
    // new initialization:
    if (true) {
      val glContext = new GLContext()
      val contextAttribs = setupContext
      
      // the problem is not the width/height of the window, other values do work...
      setupDisplay(hmd.WindowsPos.x, hmd.WindowsPos.y, hmd.Resolution.w, hmd.Resolution.h)
      
      // the following makes the difference: passing contextAttribs solves the "invalid memory access" issue, not passing it crashes
      // Display.create(new PixelFormat(/*Alpha Bits*/8, /*Depth bits*/ 8, /*Stencil bits*/ 0, /*samples*/8))
      Display.create(new PixelFormat(/*Alpha Bits*/8, /*Depth bits*/ 8, /*Stencil bits*/ 0, /*samples*/8), contextAttribs)
      Display.setVSyncEnabled(false)
      
      // the following three things do not seem to be the cause, can be commented out?
      GLContext.useContext(glContext, false)
      Mouse.create()
      Keyboard.create()    
    } else {
      // this is the old version that crashes (? or crashed) with an "invalid memory access" in configureRendering
      Display.setDisplayMode(new DisplayMode(1280, 800))
      Display.setVSyncEnabled(true)
      Display.create(new PixelFormat(/*Alpha Bits*/8, /*Depth bits*/ 8, /*Stencil bits*/ 0, /*samples*/8))     
    }
    println(f"OpenGL version: ${GL11.glGetString(GL11.GL_VERSION)}")
  }
 
  
  /**
   * Some general OpenGL state settings
   */
  def configureOpenGL() {
    glClearColor(78/255f, 115/255f, 151/255f, 0.0f)

    glClearDepth(1.0f)
    glEnable(GL_DEPTH_TEST)
    glDepthFunc(GL_LEQUAL)

    glEnable(GL_CULL_FACE)
    glCullFace(GL_BACK) // which side should be suppressed? typically the "back" side

    glEnable(GL_BLEND)
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

    // for wire-frame:
    //glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)
    //glDisable(GL_CULL_FACE)
  }  
  



  /**
   * Main
   */
  def main(args: Array[String]) {
    
    // initialize the Oculus Rift
    val hmd = initHmd() 
    
    // initialize and configure OpenGL
    initOpenGL(hmd)
    configureOpenGL()
    
    Mouse.setGrabbed(true)
    //Mouse.setNativeCursor(arg0)
    
    // start tracking
    hmd.configureTracking(ovrTrackingCap_Orientation | ovrTrackingCap_Position | ovrTrackingCap_MagYawCorrection, 0)
    
    // prepare fovports
    val fovPorts = Array.tabulate(2)(eye => hmd.DefaultEyeFov(eye))
    val projections = Array.tabulate(2)(eye => Mat4f.createFromRowMajorArray(Hmd.getPerspectiveProjection(fovPorts(eye), 0.0001f, 10000f, true).M))
    
    val oversampling = 1.0f
    
    val eyeTextures = new GLTexture().toArray(2).asInstanceOf[Array[GLTexture]]
    Range(0, 2).foreach{ eye =>
      val header = eyeTextures(eye).ogl.Header
      header.TextureSize = hmd.getFovTextureSize(eye, fovPorts(eye), oversampling)
      header.RenderViewport.Size = header.TextureSize
      header.RenderViewport.Pos = new OvrVector2i(0, 0)
      header.API = OvrLibrary.ovrRenderAPIType.ovrRenderAPI_OpenGL
    }
    // the eyeTextures must be contiguous, since they are passed to endFrame
    checkContiguous(eyeTextures)
    
    
    val framebuffers = Array.tabulate(2){eye => 
      new FramebufferTexture(eyeTextures(eye).ogl.Header.TextureSize.w, eyeTextures(eye).ogl.Header.TextureSize.h)
      //new MultisampleFramebufferTexture(eyeTextures(eye).Header.TextureSize.w, eyeTextures(eye).Header.TextureSize.h, 4)
    }
    
    for (eye <- Range(0, 2)) {
      eyeTextures(eye).ogl.TexId = framebuffers(eye).textureId
      println(f"Texture ID of eye $eye: ${eyeTextures(eye).ogl.TexId}")
    }

    val rc = new RenderAPIConfig()
    rc.Header.API = OvrLibrary.ovrRenderAPIType.ovrRenderAPI_OpenGL
    rc.Header.BackBufferSize = hmd.Resolution
    rc.Header.Multisample = 1 // does not seem to have any effect
    
    val distortionCaps = 
      //ovrDistortionCap_NoSwapBuffers |
      //ovrDistortionCap_FlipInput |
      ovrDistortionCap_TimeWarp |
      ovrDistortionCap_Overdrive |
      //ovrDistortionCap_HqDistortion |
      ovrDistortionCap_Chromatic | 
      ovrDistortionCap_Vignette | 
      ovrDistortionCap_NoRestore | 
      //ovrDistortionCap_LinuxDevFullscreen |
      //ovrDistortionCap_ProfileNoTimewarpSpinWaits |
      0
    
    // configure rendering
    GlWrapper.checkGlError("before configureRendering")
    val eyeRenderDescs = hmd.configureRendering(rc, distortionCaps, fovPorts)
    GlWrapper.checkGlError("after configureRendering")
    
    // hmdToEyeViewOffset is an Array[OvrVector3f] and is needed in the GetEyePoses call
    // we can prepare this here. Note: must be a contiguous structure
    val hmdToEyeViewOffsets = new OvrVector3f().toArray(2).asInstanceOf[Array[OvrVector3f]]
    Range(0, 2).foreach { eye =>
      hmdToEyeViewOffsets(eye).x = eyeRenderDescs(eye).HmdToEyeViewOffset.x
      hmdToEyeViewOffsets(eye).y = eyeRenderDescs(eye).HmdToEyeViewOffset.y
      hmdToEyeViewOffsets(eye).z = eyeRenderDescs(eye).HmdToEyeViewOffset.z
    }
    checkContiguous(hmdToEyeViewOffsets)
    
    
    
    
    // mutable model/world transformation
    var modelR = Mat4f.createIdentity
    var modelS = Mat4f.createIdentity.scale(1f/100,1f/100,1f/100)
    var modelT = Mat4f.translate(-0.5f, -0.5f, -1)
    

    // nested function for handling a few keyboard controls
    def handleKeyboardInput(dt: Float) {
      val ds = 0.001f * dt   //   1 m/s
      val da = 0.09f  * dt   //  90 °/s

      val mouseScroll = Mouse.getDWheel() / 120
      
      import Keyboard._
      while (Keyboard.next()) {
        val (isKeyPress, key, char) = (Keyboard.getEventKeyState(), Keyboard.getEventKey(), Keyboard.getEventCharacter())
        if (isKeyPress) {
          key match {
            case KEY_F1 => // currently nothing
            case _ => {}
          }
        }
      } 
      if (Keyboard.isKeyDown(KEY_RIGHT))  modelT = modelT.translate(-ds, 0, 0)
      if (Keyboard.isKeyDown(KEY_LEFT))   modelT = modelT.translate(+ds, 0, 0)
      if (Keyboard.isKeyDown(KEY_UP))     modelT = modelT.translate(0, +ds, 0)
      if (Keyboard.isKeyDown(KEY_DOWN))   modelT = modelT.translate(0, -ds, 0)
      if (Keyboard.isKeyDown(KEY_PRIOR))  modelT = modelT.translate(0, 0, -ds)
      if (Keyboard.isKeyDown(KEY_NEXT))   modelT = modelT.translate(0, 0, +ds)

      if (Keyboard.isKeyDown(KEY_S))      modelT = modelT.translate(0, +ds, 0)
      if (Keyboard.isKeyDown(KEY_W))      modelT = modelT.translate(0, -ds, 0)
      if (Keyboard.isKeyDown(KEY_D))      modelT = modelT.translate(-ds, 0, 0)
      if (Keyboard.isKeyDown(KEY_A))      modelT = modelT.translate(+ds, 0, 0)

      if (mouseScroll != 0 && !Keyboard.isKeyDown(KEY_LCONTROL)) {
        modelT = modelT.translate(0, 0, mouseScroll*0.1f)
      }
      if (mouseScroll != 0 && Keyboard.isKeyDown(KEY_LCONTROL)) {
        val dS = 0.05f
        modelS = modelS.scale(1+dS*mouseScroll, 1+dS*mouseScroll, 1f+dS*mouseScroll)
      }
      
    }
    

    val dungeon = DungeonGenerator.generate
    val dungeonRenderer = new DungeonRenderer(dungeon)

    // frame timing vars
    var numFrames = 0L
    val t1 = System.currentTimeMillis()
    var tL = t1
    
    val trackingLogger: Option[RiftTrackingLogger] = None // Some(new RiftTrackingLogger)
    
    // main loop:  
    while (!Display.isCloseRequested()) {
      
      val tN = System.currentTimeMillis()
      val dt = tN-tL
      tL = tN
      
      handleKeyboardInput(dt)

      GlWrapper.checkGlError("beginning of main loop")
      
      // deal with HSW
      val hswState = hmd.getHSWDisplayState()
      if (hswState.Displayed != 0) {
        hmd.dismissHSWDisplay()
      }
      
      // start frame timing
      val frameTiming = hmd.beginFrame(numFrames.toInt)
      
      trackingLogger.map(_.writeTrackingState(hmd, frameTiming))
      
      // get tracking by getEyePoses
      val headPoses = hmd.getEyePoses(numFrames.toInt, hmdToEyeViewOffsets)
      checkContiguous(headPoses)
      
      val nextFrameDelta = (frameTiming.NextFrameSeconds-frameTiming.ThisFrameSeconds)*1000
      val scanoutMidpointDelta = (frameTiming.ScanoutMidpointSeconds-frameTiming.ThisFrameSeconds)*1000
      val timewarpDelta = (frameTiming.TimewarpPointSeconds-frameTiming.ThisFrameSeconds)*1000
      //println(f"delta = ${frameTiming.DeltaSeconds*1000}%9.3f thisFrame = ${frameTiming.ThisFrameSeconds*1000}%9.3f    nextFrameΔ = ${nextFrameDelta}%9.3f    timewarpΔ =  ${timewarpDelta}%9.3f    scanoutMidpointΔ = ${scanoutMidpointDelta}%9.3f")

      // now iterate eyes
      for (i <- 0 until 2) {
        val eye = hmd.EyeRenderOrder(i)
        val P = projections(eye)

        val pose = headPoses(eye)
        
        //println(f"tracking position: x = ${pose.Position.x}%8.3f    y = ${pose.Position.y}%8.3f    z = ${pose.Position.z}%8.3f")

        //val trackingScale = 0.1f
        val matPos = Mat4f.translate(-pose.Position.x, -pose.Position.y, -pose.Position.z) //.scale(trackingScale, trackingScale, trackingScale)
        val matOri = new Quaternion(-pose.Orientation.x, -pose.Orientation.y, -pose.Orientation.z, pose.Orientation.w).castToOrientationMatrix // RH
        val V = matOri * matPos * modelT*modelR*modelS // V*modelT*modelR*modelS
        
        // the old transformation was: matEye * matOri * matPos
        // the matEye correction is no longer needed, since the eye offset is now incorporated into pose.position
        // val matEye = Mat4f.translate(eyeRenderDescs(eye).HmdToEyeViewOffset.x, eyeRenderDescs(eye).HmdToEyeViewOffset.y, eyeRenderDescs(eye).HmdToEyeViewOffset.z)
        
        framebuffers(eye).activate()
        dungeonRenderer.render(P, V)
        framebuffers(eye).deactivate()

        GlWrapper.checkGlError("after hmd.endEyeRender()")
      }
      
      GlWrapper.checkGlError("before hmd.endFrame()")
      hmd.endFrame(headPoses, eyeTextures)
      GlWrapper.checkGlError("after hmd.endFrame()")

      Display.processMessages()
      //Display.update()
      //Display.swapBuffers()
      //Display.sync(75)
      numFrames += 1
      
    }

    val t2 = System.currentTimeMillis()
    println(f"\n *** average framerate: ${numFrames.toDouble / (t2-t1) * 1000}%.1f fps")

    trackingLogger.map(_.close())
    
    // destroy Hmd
    hmd.destroy()
    // OvrLibrary.INSTANCE.ovr_Shutdown() // apparently no longer required, causes buffer overflow
    println("Hmd destroyed")
    
    // destroy display
    Display.destroy()
    println("Display destroyed")
    
    System.exit(0)
  }
  
  
  private def checkContiguous[T <: Structure](ts: Array[T]) {
    val first = ts(0).getPointer
    val size = ts(0).size
    val secondCalc = first.getPointer(size)
    val secondActual = ts(1).getPointer.getPointer(0)
    assert(secondCalc == secondActual, "array must be contiguous in memory.")
  }
 
}



