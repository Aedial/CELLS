#!/usr/bin/env python3
"""
Wave Texture Generator - Permanent build-time texture generation script.

Takes grayscale tint textures from tools/tints/ and generates animated rotating wave
textures. A wave line of configurable width sweeps around the center, with maximum
darkening at its center and fading to minimum darkening at its edges.

FIXME: need to find a nicer way to generate the wave, the current way looks awful. It's either :
- Unotticeable (previous state)
- Disruptive (too fast)
- Smudgy
Animation is kept disabled for now


Parameters:
  --wave-width: Total width of wave line in degrees (default: 90)
  --min-darkening: Darkening at edges of wave (0-1, default: 0)
  --max-darkening: Darkening at center of wave (0-1, default: 0.4)
  --darkening-function: Fade function (linear/quadratic/cosine, default: linear)
  --ticks-per-frame: Game ticks per animation frame (default: 1)
  --frames-per-360: Number of unique frames per full 360° rotation (default: 30)
  --output-dir: Output directory for generated textures

Usage:
  python generate_wave_textures.py                        # Generate with defaults
  python generate_wave_textures.py --wave-width 120       # Wider wave line
  python generate_wave_textures.py --max-darkening 0.6    # Stronger center darkening
  python generate_wave_textures.py --darkening-function cosine  # Smoother fade
"""

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image


# Configuration
TIERS = ['1k', '4k', '16k', '64k', '256k', '1m', '4m', '16m', '64m', '256m', '1g', '2g']
FRAME_SIZE = 16


@dataclass
class WaveConfig:
    """Configuration for wave animation generation."""
    wave_width: float = 90.0          # Total width of the wave line in degrees
    min_darkening: float = 0.0        # Darkening at edges of the wave (0 = no effect)
    max_darkening: float = 0.4        # Darkening at center of the wave (1 = full black)
    darkening_function: str = 'linear'  # 'linear', 'quadratic', 'cosine'
    ticks_per_frame: int = 1          # Game ticks per animation frame
    frames_per_360: int = 30          # Frames for full 360° rotation


def get_tints_path() -> Path:
    """Get the path to grayscale tint textures."""
    return Path(__file__).parent / 'tints'


def get_output_path() -> Path:
    """Get the default output path for generated textures."""
    return Path(__file__).parent.parent / 'src/main/resources/assets/cells/textures/items/cells/component_core'


def load_tint(tier: str) -> np.ndarray | None:
    """Load a grayscale tint texture for a tier."""
    tint_path = get_tints_path() / f'tint_{tier}.png'
    
    if not tint_path.exists():
        return None
    
    img = Image.open(tint_path).convert('RGBA')
    return np.array(img)


def apply_darkening_function(t: float, func: str) -> float:
    """
    Apply darkening function to normalized distance from line center.
    
    Args:
        t: Distance from center, 0 = at center, 1 = at edge
        func: Function name ('linear', 'quadratic', 'cosine')
    
    Returns:
        Interpolation factor (0 = max darkening, 1 = min darkening)
    """
    if func == 'linear':
        return t
    elif func == 'quadratic':
        return t * t
    elif func == 'cosine':
        return (1 - np.cos(t * np.pi)) / 2
    else:
        return t  # fallback to linear


def generate_wave_frame(tint: np.ndarray, phase: float, config: WaveConfig) -> np.ndarray:
    """
    Generate a single frame of the rotating wave animation.
    
    A line of width wave_width sweeps around the center. The line has max
    darkening at its center and fades to min darkening at its edges
    (±wave_width/2 from center). Beyond the edges, pixels are unaffected.
    
    Args:
        tint: 16x16 RGBA grayscale tint texture
        phase: Animation phase in radians (0 to 2π for full rotation)
        config: Wave configuration parameters
    
    Returns:
        16x16 RGBA frame with wave applied
    """
    result = np.zeros((FRAME_SIZE, FRAME_SIZE, 4), dtype=np.uint8)
    
    center_x = (FRAME_SIZE - 1) / 2
    center_y = (FRAME_SIZE - 1) / 2
    
    half_width_rad = np.radians(config.wave_width) / 2
    
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            r, g, b, a = tint[y, x]
            
            # Skip transparent pixels
            if a < 128:
                continue
            
            # Calculate angle from center (atan2 gives -π to π)
            dx = x - center_x
            dy = y - center_y
            angle = np.arctan2(dy, dx)  # -π to π
            
            # Calculate angular distance from the wave line center
            angle_diff = angle - phase
            # Normalize to -π to π
            while angle_diff > np.pi:
                angle_diff -= 2 * np.pi
            while angle_diff < -np.pi:
                angle_diff += 2 * np.pi
            
            abs_diff = abs(angle_diff)
            
            # Check if pixel is within the wave line
            if abs_diff <= half_width_rad:
                # Distance from center: 0 at center, 1 at edge
                t = abs_diff / half_width_rad
                # Apply function to get interpolation factor
                interp = apply_darkening_function(t, config.darkening_function)
                # Interpolate: at center (t=0) -> max_darkening, at edge (t=1) -> min_darkening
                darkening = config.max_darkening + interp * (config.min_darkening - config.max_darkening)
                brightness = 1 - darkening
            else:
                brightness = 1.0
            
            # Apply brightness to grayscale value
            gray = r
            new_gray = int(np.clip(gray * brightness, 0, 255))
            
            result[y, x] = [new_gray, new_gray, new_gray, 255]
    
    return result


def generate_animation(tint: np.ndarray, config: WaveConfig) -> list[np.ndarray]:
    """
    Generate all frames of the wave animation.
    
    Args:
        tint: 16x16 RGBA grayscale tint texture
        config: Wave configuration parameters
    
    Returns:
        List of 16x16 RGBA frames
    """
    frames = []
    
    for i in range(config.frames_per_360):
        phase = (i / config.frames_per_360) * 2 * np.pi
        frame = generate_wave_frame(tint, phase, config)
        frames.append(frame)
    
    return frames


def frames_equal(frame1: np.ndarray, frame2: np.ndarray) -> bool:
    """Check if two frames are identical."""
    return np.array_equal(frame1, frame2)


def deduplicate_frames(frames: list[np.ndarray]) -> tuple[list[np.ndarray], list[int]]:
    """
    Deduplicate frames and return unique frames + frame ordering.
    
    Returns:
        (unique_frames, frame_indices) where frame_indices[i] gives the
        index into unique_frames for the i-th animation frame.
    """
    unique_frames = []
    frame_indices = []
    
    for frame in frames:
        # Check if this frame matches any existing unique frame
        found_idx = -1
        for idx, unique in enumerate(unique_frames):
            if frames_equal(frame, unique):
                found_idx = idx
                break
        
        if found_idx >= 0:
            frame_indices.append(found_idx)
        else:
            frame_indices.append(len(unique_frames))
            unique_frames.append(frame)
    
    return unique_frames, frame_indices


def save_animation(unique_frames: list[np.ndarray], frame_indices: list[int],
                   output_path: Path, tier: str, config: WaveConfig) -> None:
    """
    Save animation as vertical strip texture + mcmeta file.
    
    Args:
        unique_frames: List of unique frames
        frame_indices: Animation frame order (indices into unique_frames)
        output_path: Directory to save to
        tier: Tier name for filename
        config: Wave configuration for mcmeta
    """
    # Stack unique frames vertically
    height = FRAME_SIZE * len(unique_frames)
    strip = np.zeros((height, FRAME_SIZE, 4), dtype=np.uint8)
    
    for i, frame in enumerate(unique_frames):
        strip[i * FRAME_SIZE:(i + 1) * FRAME_SIZE] = frame
    
    # Save texture
    texture_path = output_path / f'tint_{tier}.png'
    Image.fromarray(strip).save(texture_path)
    
    # Create mcmeta with frame ordering
    mcmeta = {
        "animation": {
            "frametime": config.ticks_per_frame,
            "width": FRAME_SIZE,
            "height": FRAME_SIZE,
            "interpolate": False,
            "frames": frame_indices
        }
    }
    
    mcmeta_path = output_path / f'tint_{tier}.png.mcmeta'
    with open(mcmeta_path, 'w') as f:
        json.dump(mcmeta, f, indent=2)


def main():
    parser = argparse.ArgumentParser(description='Generate rotating wave animation textures')
    parser.add_argument('--wave-width', type=float, default=90.0,
                        help='Total width of wave line in degrees (default: 90)')
    parser.add_argument('--min-darkening', type=float, default=0.0,
                        help='Darkening at edges of wave (0-1, default: 0)')
    parser.add_argument('--max-darkening', type=float, default=0.4,
                        help='Darkening at center of wave (0-1, default: 0.4)')
    parser.add_argument('--darkening-function', type=str, default='linear',
                        choices=['linear', 'quadratic', 'cosine'],
                        help='Fade function from center to edge (default: linear)')
    parser.add_argument('--ticks-per-frame', type=int, default=1,
                        help='Game ticks per animation frame (default: 1)')
    parser.add_argument('--frames-per-360', type=int, default=30,
                        help='Number of frames per full rotation (default: 30)')
    parser.add_argument('--output-dir', type=str, default=None,
                        help='Output directory (default: textures/items/cells/component_core)')
    
    args = parser.parse_args()
    
    config = WaveConfig(
        wave_width=args.wave_width,
        min_darkening=args.min_darkening,
        max_darkening=args.max_darkening,
        darkening_function=args.darkening_function,
        ticks_per_frame=args.ticks_per_frame,
        frames_per_360=args.frames_per_360,
    )
    
    tints_path = get_tints_path()
    output_path = Path(args.output_dir) if args.output_dir else get_output_path()
    output_path.mkdir(parents=True, exist_ok=True)
    
    print("=" * 70)
    print("WAVE TEXTURE GENERATION")
    print("=" * 70)
    print(f"Tints from: {tints_path}")
    print(f"Output to:  {output_path}")
    print()
    print(f"Wave width:      {config.wave_width}° (±{config.wave_width/2}° from center)")
    print(f"Darkening:       {config.min_darkening:.0%} (edge) -> {config.max_darkening:.0%} (center)")
    print(f"Function:        {config.darkening_function}")
    print(f"Ticks/frame:     {config.ticks_per_frame}")
    print(f"Frames/360°:     {config.frames_per_360}")
    print()
    print("-" * 70)
    
    total_frames = 0
    total_unique = 0
    
    for tier in TIERS:
        tint = load_tint(tier)
        
        if tint is None:
            print(f"{tier}: SKIPPED (no tint found)")
            continue
        
        # Count active pixels
        active_pixels = np.sum(tint[:, :, 3] > 128)
        if active_pixels == 0:
            print(f"{tier}: SKIPPED (empty tint)")
            continue
        
        # Generate animation
        frames = generate_animation(tint, config)
        unique_frames, frame_indices = deduplicate_frames(frames)
        
        # Save
        save_animation(unique_frames, frame_indices, output_path, tier, config)
        
        total_frames += len(frames)
        total_unique += len(unique_frames)
        
        dedup_ratio = len(unique_frames) / len(frames) * 100
        print(f"{tier}: {len(frames)} frames -> {len(unique_frames)} unique ({dedup_ratio:.0f}%)")
    
    print()
    print("-" * 70)
    print(f"Total: {total_frames} frames -> {total_unique} unique "
          f"({total_unique / total_frames * 100:.0f}%)" if total_frames else "No textures generated")
    print()
    print("Done!")


if __name__ == '__main__':
    main()
