package co.ferreteria.inventario.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
}

interface MarcaRepository extends JpaRepository<Marca, Integer> {
    Optional<Marca> findByNombreIgnoreCase(String nombre);
}

interface ProductoRepository extends JpaRepository<Producto, Integer> {
    Optional<Producto> findByCodigoInterno(String codigoInterno);
}

interface ReferenciaRepository extends JpaRepository<Referencia, Integer> {
    boolean existsByCodigoBarras(String codigoBarras);
}
