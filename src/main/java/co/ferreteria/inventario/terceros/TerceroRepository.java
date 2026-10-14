package co.ferreteria.inventario.terceros;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TerceroRepository extends JpaRepository<Tercero, Long> {
    Optional<Tercero> findByTipoIdentificacionAndNumeroIdentificacion(String tipo, String numero);

    @Query("""
            SELECT t FROM Tercero t
             WHERE (:texto = '' OR t.numeroIdentificacion = :texto
                    OR lower(coalesce(t.razonSocial, concat(t.nombres, ' ', t.apellidos))) LIKE lower(concat('%', :texto, '%')))
               AND (:cliente = false OR t.esCliente = true)
               AND (:proveedor = false OR t.esProveedor = true)
             ORDER BY coalesce(t.razonSocial, t.nombres)""")
    List<Tercero> buscar(@Param("texto") String texto, @Param("cliente") boolean cliente,
                         @Param("proveedor") boolean proveedor, Pageable pagina);
}
