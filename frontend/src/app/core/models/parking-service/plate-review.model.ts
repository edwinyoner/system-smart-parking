// src/app/core/models/parking-service/plate-review.model.ts
// Cola "Por revisar": lecturas dudosas de las cámaras (/parking-service/v1/plate-reviews).

export type PlateReviewStatus = 'PENDING' | 'CONFIRMED' | 'DISCARDED';
export type PlateReviewDirection = 'ENTRY' | 'EXIT';

export interface PlateReview {
  id: number;
  captureId: string;
  direction: PlateReviewDirection;
  parkingId: number;
  parkingName?: string | null;
  zoneId?: number | null;
  zoneName?: string | null;
  deviceId?: string | null;
  detectedPlate?: string | null;    // lo que leyó la IA, tal cual
  suggestedPlate?: string | null;   // la lectura ya normalizada, si tiene formato válido
  confidence: number;               // 0 a 1
  photoUrl?: string | null;
  status: PlateReviewStatus;
  confirmedPlate?: string | null;
  transactionId?: number | null;
  discardReason?: string | null;
  resolvedBy?: number | null;
  resolvedAt?: string | null;
  createdAt: string;
}

export interface ConfirmPlateReviewRequest {
  plateNumber: string;
  documentTypeId?: number | null;
  documentNumber?: string | null;
}
