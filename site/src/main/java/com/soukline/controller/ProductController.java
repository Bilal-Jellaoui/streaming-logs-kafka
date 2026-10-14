package com.soukline.controller;

import com.soukline.model.Product;
import com.soukline.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/produits")
    public String produits(
            @RequestParam(value = "categorie", required = false) String categorie,
            @RequestParam(value = "tri", required = false, defaultValue = "default") String tri,
            Model model) {

        List<Product> products = productService.filterAndSort(categorie, null, tri);
        model.addAttribute("products", products);
        model.addAttribute("categories", productService.getAllCategories());
        model.addAttribute("selectedCategory", categorie);
        model.addAttribute("selectedTri", tri);
        model.addAttribute("totalCount", products.size());
        return "produits";
    }

    @GetMapping("/produits/{id}")
    public String produitDetail(@PathVariable("id") Long id, Model model) {
        Product product = productService.getProductById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit #" + id + " introuvable"));

        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", productService.getProductsByCategory(product.getCategory()));
        return "produit-detail";
    }

    @GetMapping("/recherche")
    public String recherche(
            @RequestParam(value = "q", required = false, defaultValue = "") String query,
            Model model) {

        List<Product> results = productService.searchProducts(query);
        model.addAttribute("query", query);
        model.addAttribute("products", results);
        model.addAttribute("resultsCount", results.size());
        model.addAttribute("categories", productService.getAllCategories());
        return "recherche";
    }
}
