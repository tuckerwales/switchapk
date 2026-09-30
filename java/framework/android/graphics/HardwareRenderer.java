package android.graphics;

public class HardwareRenderer {
    public HardwareRenderer() {}
    public void destroy() {}
    public void setName(String name) {}
    public void setOpaque(boolean opaque) {}
    public boolean isOpaque() { return true; }
    public void setContentRoot(RenderNode content) {}
    public void setSurface(android.view.Surface surface) {}
    public void stop() {}
    public void start() {}
    public void notifyFramePending() {}
}
