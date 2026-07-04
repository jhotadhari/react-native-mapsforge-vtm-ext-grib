module.exports = {
	dependency: {
		platforms: {
			android: {
				sourceDir: './android',
				packageImportPath:
					'import com.jhotadhari.reactnative.mapsforge.vtm.ext.grib.ExtGribPackage;',
				packageInstance: 'new ExtGribPackage()',
			},
		},
	},
};
