package com.winnersystems.smartparking.parking.application.port.input.publicinfo;

import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto;

import java.util.List;

/**
 * Consultas públicas (sin login) de los parqueos: app móvil y paneles LED.
 */
public interface GetPublicParkingInfoUseCase {

   /** Parqueos visibles para el público (no eliminados ni desactivados), por nombre */
   List<PublicParkingDto.ParkingCard> listParkings();

   /** Detalle con zonas y horario. 404 si no existe, está eliminado o desactivado. */
   PublicParkingDto.ParkingDetail getParking(Long parkingId);

   /** Disponibilidad compacta para los paneles. 404 en los mismos casos. */
   PublicParkingDto.Availability getAvailability(Long parkingId);

   /** Mapa de espacios por zona, sin placas. 404 en los mismos casos. */
   PublicParkingDto.SpaceMap getSpaceMap(Long parkingId);
}
