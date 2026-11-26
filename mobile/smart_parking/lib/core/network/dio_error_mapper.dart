import 'package:dio/dio.dart';

import '../errors/failures.dart';

/// Convierte un error de Dio en un [Failure] propio.
Failure mapDioException(DioException e) {
  switch (e.type) {
    case DioExceptionType.connectionTimeout:
    case DioExceptionType.sendTimeout:
    case DioExceptionType.receiveTimeout:
    case DioExceptionType.connectionError:
      return const NetworkFailure();
    case DioExceptionType.badResponse:
      final status = e.response?.statusCode;
      if (status == 404) return const NotFoundFailure();
      return ServerFailure(status);
    default:
      return const UnexpectedFailure();
  }
}