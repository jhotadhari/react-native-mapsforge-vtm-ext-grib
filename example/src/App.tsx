import { View, Text, StyleSheet, useWindowDimensions } from 'react-native';
import {
	LayerBitmapTile,
	LayerScalebar,
	MapContainer,
} from 'react-native-mapsforge-vtm';
import { WeatherOverlay } from 'react-native-mapsforge-vtm-ext-grib';

/**
 * Weather overlay example.
 *
 * Before running, serve the sample data file so the native module can fetch it:
 *   cd example/data && python3 -m http.server 8000
 *
 * Then update DATA_URL below to point to your machine's IP (or 10.0.2.2 for
 * the Android emulator host loopback).
 */
const DATA_URL = 'http://10.0.2.2:8000/sample-wind.json';

// Central Germany — the sample wind data covers 48-54°N, 6-14°E
const defaultCenter: [number, number] = [10, 51];

export default function App() {
	const { width, height } = useWindowDimensions();

	return (
		<View style={styles.container}>
			<MapContainer
				width={width}
				height={height}
				center={defaultCenter}
				zoomLevel={6}
			>
				<LayerBitmapTile />
				<LayerScalebar />
				<WeatherOverlay
					dataUrl={DATA_URL}
					parameter="WIND"
					timeIndex={0}
					colorMap="wind"
					opacity={0.7}
					onError={(err) => {
						console.warn('WeatherOverlay error:', err?.userInfo?.errorMsg ?? err);
					}}
				/>
			</MapContainer>

			<View style={styles.overlay}>
				<Text style={styles.text}>WIND @ surface</Text>
				<Text style={styles.hint}>
					Serve data: python3 -m http.server 8000
				</Text>
			</View>
		</View>
	);
}

const styles = StyleSheet.create({
	container: {
		flex: 1,
		backgroundColor: '#1a1a1a',
	},
	overlay: {
		position: 'absolute',
		top: 16,
		left: 16,
		backgroundColor: 'rgba(0,0,0,0.65)',
		paddingHorizontal: 12,
		paddingVertical: 8,
		borderRadius: 6,
	},
	text: {
		color: '#fff',
		fontSize: 14,
		fontWeight: '600',
	},
	hint: {
		color: '#aaa',
		fontSize: 10,
		marginTop: 4,
	},
});
