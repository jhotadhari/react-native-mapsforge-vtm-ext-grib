import { useCallback, useMemo } from 'react';
import {
	useSharedValue,
	withTiming,
	type SharedValue,
} from 'react-native-reanimated';

export interface WeatherAnimationControls {
	/** The current animation progress (0.0 to timeSteps.length - 1). */
	progressSv: SharedValue<number>;
	/** Start playing the animation. */
	play: () => void;
	/** Pause the animation. */
	pause: () => void;
	/** Reset to the first time step. */
	reset: () => void;
	/** Seek to a specific time step index (fractional for interpolation). */
	seek: (timeIndex: number) => void;
}

/**
 * Creates a reanimated shared value that drives smooth time-step animation
 * for a weather overlay.
 *
 * The progress value ranges from 0.0 to (numTimeSteps - 1), where the
 * fractional part represents the interpolation between adjacent time steps.
 *
 * Usage with WeatherOverlay:
 * - Pass `Math.floor(progress.value)` as `timeIndex` to the "current" layer
 * - Pass `Math.ceil(progress.value)` as `timeIndex` to the "next" layer
 * - Set the "next" layer's opacity to `progress.value - Math.floor(progress.value)`
 *
 * @param numTimeSteps - Total number of forecast time steps available.
 * @param durationMs - Duration of the full animation in milliseconds.
 * @returns Animation controls including the progress shared value.
 *
 * @example
 * ```tsx
 * const { progressSv, play, pause } = useWeatherAnimation(
 *   weatherData.timeSteps.length,
 *   10000
 * );
 *
 * // In a worklet or useDerivedValue:
 * const currentIdx = Math.floor(progressSv.value);
 * const nextAlpha = progressSv.value - currentIdx;
 * ```
 */
export function useWeatherAnimation(
	numTimeSteps: number,
	durationMs: number = 10000
): WeatherAnimationControls {
	const progressSv = useSharedValue<number>(0);
	const isPlayingSv = useSharedValue<boolean>(false);

	const play = useCallback(() => {
		'worklet';
		if (isPlayingSv.value) return;
		isPlayingSv.value = true;

		// Calculate remaining duration based on current progress
		const remainingProgress = numTimeSteps - 1 - progressSv.value;
		const remainingDuration = Math.max(
			100,
			(remainingProgress / (numTimeSteps - 1)) * durationMs
		);

		progressSv.value = withTiming(
			numTimeSteps - 1,
			{ duration: Math.round(remainingDuration) },
			(finished) => {
				if (finished) {
					isPlayingSv.value = false;
				}
			}
		);
	}, [
		numTimeSteps,
		durationMs,
		progressSv,
		isPlayingSv,
	]);

	const pause = useCallback(() => {
		'worklet';
		isPlayingSv.value = false;
		// Cancel the current timing by setting to current value
		progressSv.value = progressSv.value;
	}, [progressSv, isPlayingSv]);

	const reset = useCallback(() => {
		'worklet';
		isPlayingSv.value = false;
		progressSv.value = 0;
	}, [progressSv, isPlayingSv]);

	const seek = useCallback(
		(timeIndex: number) => {
			'worklet';
			const clamped = Math.max(0, Math.min(numTimeSteps - 1, timeIndex));
			progressSv.value = clamped;
		},
		[numTimeSteps, progressSv]
	);

	return useMemo(
		() => ({ progressSv, play, pause, reset, seek }),
		[
			progressSv,
			play,
			pause,
			reset,
			seek,
		]
	);
}

export default useWeatherAnimation;
