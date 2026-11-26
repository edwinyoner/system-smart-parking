import 'package:dio/dio.dart';

/// Rutas públicas de parking-service a través del api-gateway (sin token).
/// La base (`API_BASE_URL`) ya incluye `/api`.
class PublicParkingApi {
  const PublicParkingApi(this._dio);

  final Dio _dio;

  static const _base = '/parking-service/v1/public/parkings';

  Future<List<Map<String, dynamic>>> getParkings() async {
    final response = await _dio.get<List<dynamic>>(_base);
    return (response.data ?? const []).whereType<Map<String, dynamic>>().toList();
  }

  Future<Map<String, dynamic>> getParking(int id) async {
    final response = await _dio.get<Map<String, dynamic>>('$_base/$id');
    return response.data ?? const {};
  }

  Future<Map<String, dynamic>> getSpaceMap(int id) async {
    final response = await _dio.get<Map<String, dynamic>>('$_base/$id/spaces');
    return response.data ?? const {};
  }
}
