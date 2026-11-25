package com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.user;

import com.winnersystems.smartparking.auth.application.dto.query.UserSearchCriteria;
import com.winnersystems.smartparking.auth.application.port.output.UserPersistencePort;
import com.winnersystems.smartparking.auth.domain.model.User;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.role.entity.RoleEntity;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.role.repository.RoleRepository;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.user.entity.UserEntity;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.user.mapper.UserPersistenceMapper;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.user.repository.UserRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adaptador de persistencia para User.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserPersistencePort {

   // Letras que la búsqueda trata como iguales (la posición las empareja)
   private static final String ACCENTED = "áéíóúüñ";
   private static final String UNACCENTED = "aeiouun";
   private static final char LIKE_ESCAPE = '!';

   private final UserRepository userRepository;
   private final RoleRepository roleRepository;
   private final UserPersistenceMapper mapper;

   @Override
   public User save(User user) {
      UserEntity entity = mapper.toEntity(user);

      // Recargar roles desde la base de datos
      if (!entity.getRoles().isEmpty()) {
         Set<Long> roleIds = entity.getRoles().stream()
               .map(RoleEntity::getId)
               .collect(Collectors.toSet());

         // Cargar roles gestionados (managed) desde la base de datos
         Set<RoleEntity> managedRoles = new HashSet<>(
               roleRepository.findAllById(roleIds)
         );

         entity.setRoles(managedRoles);
      }

      UserEntity savedEntity = userRepository.save(entity);
      return mapper.toDomain(savedEntity);
   }

   @Override
   public Optional<User> findById(Long id) {
      return userRepository.findById(id)
            .filter(entity -> entity.getDeletedAt() == null)
            .map(mapper::toDomain);
   }

   @Override
   public Optional<User> findByEmail(String email) {
      return userRepository.findByEmail(email)
            .filter(entity -> entity.getDeletedAt() == null)
            .map(mapper::toDomain);
   }

   @Override
   public List<User> findByCriteria(UserSearchCriteria criteria, int page, int size) {
      // Construir Specification
      Specification<UserEntity> spec = buildSpecification(criteria);

      // Ordenamiento
      Sort.Direction direction = "desc".equalsIgnoreCase(criteria.sortDirection())
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;

      Sort sort = Sort.by(direction, criteria.sortBy());
      Pageable pageable = PageRequest.of(page, size, sort);

      // Ejecutar query
      Page<UserEntity> pageResult = userRepository.findAll(spec, pageable);

      // Mapear a dominio
      return pageResult.getContent().stream()
            .map(mapper::toDomain)
            .toList();
   }

   @Override
   public long countByCriteria(UserSearchCriteria criteria) {
      Specification<UserEntity> spec = buildSpecification(criteria);
      return userRepository.count(spec);
   }

   // ========== NUEVO MÉTODO AGREGADO ==========

   /**
    * Busca usuarios por rol y estado.
    * Usado para obtener operadores activos.
    *
    * @param roleName nombre del rol
    * @param status estado del usuario
    * @return lista de usuarios que cumplen los criterios
    */
   @Override
   public List<User> findByRoleAndStatus(String roleName, Boolean status) {
      List<UserEntity> entities = userRepository.findByRoleNameAndStatus(roleName, status);
      return entities.stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
   }

   // ========== HELPER: BUILD SPECIFICATION ==========

   /**
    * Construye la especificación de búsqueda sin usar métodos deprecated.
    */
   private Specification<UserEntity> buildSpecification(UserSearchCriteria criteria) {
      return (root, query, cb) -> {
         List<Predicate> predicates = new ArrayList<>();

         // BÚSQUEDA GENERAL
         if (criteria.search() != null && !criteria.search().isBlank()) {
            // Todo en un solo texto: así "Nombre Apellido" encuentra al usuario
            Expression<String> text = cb.concat(root.get("firstName"), " ");
            text = cb.concat(text, root.get("lastName"));
            text = cb.concat(cb.concat(text, " "), root.get("email"));
            text = cb.concat(cb.concat(text, " "), cb.coalesce(root.<String>get("phoneNumber"), ""));
            Expression<String> searchable = cb.function("translate", String.class,
                  cb.lower(text), cb.literal(ACCENTED), cb.literal(UNACCENTED));

            // Deben aparecer todas las palabras, en cualquier orden
            for (String word : normalize(criteria.search()).split("\\s+")) {
               predicates.add(cb.like(searchable, "%" + escapeLike(word) + "%", LIKE_ESCAPE));
            }
         }

         // FILTRO POR ROL
         if (criteria.roleId() != null) {
            Join<UserEntity, RoleEntity> rolesJoin = root.join("roles");
            predicates.add(cb.equal(rolesJoin.get("id"), criteria.roleId()));
         }

         // FILTRO POR ESTADO
         if (criteria.status() != null) {
            predicates.add(cb.equal(root.get("status"), criteria.status()));
         }

         // FILTRO POR EMAIL VERIFICADO
         if (criteria.emailVerified() != null) {
            predicates.add(cb.equal(root.get("emailVerified"), criteria.emailVerified()));
         }

         // EXCLUIR ELIMINADOS
         predicates.add(cb.isNull(root.get("deletedAt")));

         // COMBINAR TODOS LOS PREDICADOS
         return cb.and(predicates.toArray(new Predicate[0]));
      };
   }

   /** Minúsculas y sin tildes, igual que el texto con el que se compara. */
   private static String normalize(String value) {
      String result = value.strip().toLowerCase(Locale.ROOT);
      for (int i = 0; i < ACCENTED.length(); i++) {
         result = result.replace(ACCENTED.charAt(i), UNACCENTED.charAt(i));
      }
      return result;
   }

   /** Evita que '%' y '_' (frecuente en correos) actúen como comodines. */
   private static String escapeLike(String word) {
      return word.replace("!", "!!").replace("%", "!%").replace("_", "!_");
   }
}