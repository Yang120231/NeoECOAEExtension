package cn.dancingsnow.neoecoae.client.multiblock.preview;

final class CameraFit {
    private CameraFit() {}

    /**
     * Calculate a stable scale based solely on the scene's bounding sphere radius,
     * independent of yaw/pitch. This prevents scale breathing during rotation.
     */
    static float calculateStableScale(SceneBounds bounds, int width, int height, float padding) {
        float sizeX = bounds.sizeX();
        float sizeY = bounds.sizeY();
        float sizeZ = bounds.sizeZ();
        float radius = (float) Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ) * 0.5F;
        float scale = Math.min(width, height) * padding / Math.max(1.0F, radius * 2.0F);
        return scale;
    }

    static ProjectedBounds project(SceneBounds bounds, float yaw, float pitch) {
        float centerX = bounds.centerX();
        float centerY = bounds.centerY();
        float centerZ = bounds.centerZ();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);

        float minScreenX = Float.POSITIVE_INFINITY;
        float maxScreenX = Float.NEGATIVE_INFINITY;
        float minScreenY = Float.POSITIVE_INFINITY;
        float maxScreenY = Float.NEGATIVE_INFINITY;

        for (float cornerX : new float[] {bounds.minX(), bounds.maxX() + 1.0F}) {
            for (float cornerY : new float[] {bounds.minY(), bounds.maxY() + 1.0F}) {
                for (float cornerZ : new float[] {bounds.minZ(), bounds.maxZ() + 1.0F}) {
                    float localX = cornerX - centerX;
                    float localY = cornerY - centerY;
                    float localZ = cornerZ - centerZ;

                    float yawX = (float) (localX * cosYaw + localZ * sinYaw);
                    float yawZ = (float) (-localX * sinYaw + localZ * cosYaw);
                    float pitchY = (float) (localY * cosPitch - yawZ * sinPitch);

                    minScreenX = Math.min(minScreenX, yawX);
                    maxScreenX = Math.max(maxScreenX, yawX);
                    minScreenY = Math.min(minScreenY, pitchY);
                    maxScreenY = Math.max(maxScreenY, pitchY);
                }
            }
        }

        if (!Float.isFinite(minScreenX)
                || !Float.isFinite(maxScreenX)
                || !Float.isFinite(minScreenY)
                || !Float.isFinite(maxScreenY)) {
            return new ProjectedBounds(0.0F, 0.0F, 1.0F, 1.0F);
        }
        return new ProjectedBounds(minScreenX, maxScreenX, minScreenY, maxScreenY);
    }

    /**
     * @deprecated Use {@link #calculateStableScale} for multiblock previews.
     *             This method depends on yaw/pitch and causes scale breathing
     *             during rotation.
     */
    @Deprecated
    static float calculateScale(SceneBounds bounds, float yaw, float pitch, int width, int height, float padding) {
        ProjectedBounds projectedBounds = project(bounds, yaw, pitch);
        float projectedWidth = Math.max(1.0F, projectedBounds.width());
        float projectedHeight = Math.max(1.0F, projectedBounds.height());
        float scaleX = width * padding / projectedWidth;
        float scaleY = height * padding / projectedHeight;
        return Math.min(scaleX, scaleY);
    }

    record ProjectedBounds(float minX, float maxX, float minY, float maxY) {
        float width() {
            return maxX - minX;
        }

        float height() {
            return maxY - minY;
        }

        float centerX() {
            return (minX + maxX) * 0.5F;
        }

        float centerY() {
            return (minY + maxY) * 0.5F;
        }
    }
}
