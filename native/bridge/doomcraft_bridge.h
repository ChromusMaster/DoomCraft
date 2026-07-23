#pragma once

class DFrameBuffer;

namespace DoomCraftBridge
{
    // Called on LZDoom's render thread immediately after screen->Update().
    void OnFrame(DFrameBuffer* framebuffer);
}
