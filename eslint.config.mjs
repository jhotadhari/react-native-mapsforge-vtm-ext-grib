import { includeIgnoreFile } from '@eslint/compat';
import js from '@eslint/js';
import prettier from 'eslint-plugin-prettier';
import { defineConfig } from 'eslint/config';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default defineConfig([
	includeIgnoreFile(path.resolve(__dirname, '.prettierignore')),
	{
		plugins: { prettier },
		languageOptions: {
			ecmaVersion: 'latest',
			sourceType: 'module',
		},
		rules: {
			// Options come from .prettierrc, resolved by eslint-plugin-prettier
			'prettier/prettier': 'error',
		},
	},
	{
		ignores: [
			'node_modules/',
			'lib/',
		],
	},
]);
