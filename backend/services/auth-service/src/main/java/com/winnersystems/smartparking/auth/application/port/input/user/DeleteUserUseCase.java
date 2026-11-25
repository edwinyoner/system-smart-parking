package com.winnersystems.smartparking.auth.application.port.input.user;

/**
 * Caso de uso: ELIMINAR USUARIO (Soft Delete)
 *
 * <p>Marca un usuario como eliminado sin borrar físicamente sus datos.
 * Esto permite:</p>
 * <ul>
 *   <li>Mantener integridad referencial</li>
 *   <li>Conservar auditoría completa</li>
 *   <li>Posibilidad de restaurar el usuario</li>
 *   <li>Cumplir con requisitos legales de retención de datos</li>
 * </ul>
 *
 * <h3>Autorización requerida</h3>
 * <ul>
 *   <li>Rol: <b>ADMIN</b></li>
 *   <li>Permiso: <b>users.delete</b></li>
 * </ul>
 *
 * <h3>Efectos del soft delete</h3>
 * <ul>
 *   <li>deletedAt = fecha actual (marca como eliminado)</li>
 *   <li>deletedBy = ID del admin que elimina</li>
 *   <li>status = false (cuenta desactivada)</li>
 *   <li>Tokens revocados (no puede hacer login)</li>
 *   <li>Email de notificación enviado</li>
 * </ul>
 *
 * <h3>Restricciones</h3>
 * <ul>
 *   <li>Usuario no puede eliminarse a sí mismo</li>
 *   <li>A un ADMIN solo puede eliminarlo otro usuario con ADMIN como rol activo</li>
 *   <li>No puede eliminar usuarios ya eliminados</li>
 * </ul>
 *
 * <h3>Excepciones</h3>
 * <ul>
 *   <li><b>UserNotFoundException</b> – usuario no existe</li>
 *   <li><b>IllegalStateException</b> – ya está eliminado</li>
 *   <li><b>ProtectedUserException</b> – se elimina a sí mismo o no puede administrar a un ADMIN</li>
 * </ul>
 *
 * @see RestoreUserUseCase
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public interface DeleteUserUseCase {
   /**
    * Ejecuta el proceso de eliminación lógica (soft delete).
    *
    * <p>El usuario es marcado como eliminado, pero sus datos permanecen
    * en la base de datos para auditoría. Se revocan todas sus sesiones
    * activas y no podrá autenticarse nuevamente.</p>
    *
    * @param userId ID del usuario a eliminar
    * @param deletedBy ID del administrador que realiza la acción
    * @param actorActiveRole rol con el que inició sesión el administrador
    *
    * @throws UserNotFoundException si el usuario no existe
    * @throws IllegalStateException si ya está eliminado
    * @throws ProtectedUserException si se elimina a sí mismo o no puede administrar a un ADMIN
    */
   void executeDelete(Long userId, Long deletedBy, String actorActiveRole);
}