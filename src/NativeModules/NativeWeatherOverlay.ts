import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { Double, Int32 } from 'react-native/Libraries/Types/CodegenTypes';
import type { ErrorBase, ResponseBase } from '../types';

export interface ModuleParams {
  dataUrl?: string;
  parameter?: string;
  timeIndex?: Int32;
  colorMap?: string;
  opacity?: Double;
  zoomMin?: Int32;
  zoomMax?: Int32;
  enabledZoomMin?: Int32;
  enabledZoomMax?: Int32;
}

interface CreateLayerParams extends ModuleParams {
  nativeNodeHandle?: Int32;
  positionIndex: Int32;
}

interface RemoveLayerParams {
  nativeNodeHandle: Int32;
  uuid: string;
}

interface UpdateEnabledZoomMinMaxParams {
  nativeNodeHandle: Int32;
  uuid: string;
  enabledZoomMin?: Int32;
  enabledZoomMax?: Int32;
}

interface SetOpacityParams {
  nativeNodeHandle: Int32;
  uuid: string;
  opacity?: Double;
}

interface SetTimeIndexParams {
  nativeNodeHandle: Int32;
  uuid: string;
  timeIndex: Int32;
}

export type WeatherOverlayProps = {
  dataUrl?: string;
  parameter?: string;
  timeIndex?: number;
  colorMap?: string;
  opacity?: number;
  zoomMin?: number;
  zoomMax?: number;
  enabledZoomMin?: number;
  enabledZoomMax?: number;
  onCreate?: null | ((result: ResponseBase) => void);
  onRemove?: null | ((result: ResponseBase) => void);
  onChange?: null | ((result: ResponseBase) => void);
  onError?: null | ((err: ErrorBase) => void);
};

export interface Spec extends TurboModule {
  getConstants(): ModuleParams;
  createLayer(params: CreateLayerParams): Promise<string>;
  removeLayer(params: RemoveLayerParams): Promise<string>;
  updateEnabledZoomMinMax(
    params: UpdateEnabledZoomMinMaxParams
  ): Promise<string>;
  setOpacity(params: SetOpacityParams): Promise<string>;
  setTimeIndex(params: SetTimeIndexParams): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('WeatherOverlay');
