package co.ferreteria.inventario.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolRepository extends JpaRepository<Rol, Short> {
    List<Rol> findByCodigoIn(List<String> codigos);
}
