import type { ErrorBase } from './types';

export default function reportNativeError(
	err: ErrorBase,
	onError?: null | ((err: ErrorBase) => void)
): void {
	if (onError) {
		onError(err);
	} else if (__DEV__) {
		console.warn(
			'[react-native-mapsforge-vtm-ext-grib]',
			err?.userInfo?.errorMsg ?? 'Unknown native error'
		);
	}
}
