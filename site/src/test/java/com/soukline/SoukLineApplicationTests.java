package com.soukline;

import com.soukline.service.PanneService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SoukLineApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PanneService panneService;

    @Test
    void contextLoads() {
        assertThat(panneService).isNotNull();
    }

    @Test
    void testHomePageReturns200() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void testProduitsCatalogReturns200() throws Exception {
        mockMvc.perform(get("/produits"))
                .andExpect(status().isOk())
                .andExpect(view().name("produits"));
    }

    @Test
    void testPanneModeCauses503OnCheckout() throws Exception {
        // 1. Activer la panne
        panneService.setPanneActive(true);
        assertThat(panneService.isPanneActive()).isTrue();

        // 2. Vérifier que /checkout renvoie HTTP 503
        mockMvc.perform(get("/checkout"))
                .andExpect(status().isServiceUnavailable());

        // 3. Désactiver la panne
        panneService.setPanneActive(false);
        assertThat(panneService.isPanneActive()).isFalse();
    }

    @Test
    void testAdminPanneEndpoint() throws Exception {
        mockMvc.perform(get("/admin/panne?etat=on"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panneActive").value(true));

        assertThat(panneService.isPanneActive()).isTrue();

        mockMvc.perform(get("/admin/panne?etat=off"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panneActive").value(false));

        assertThat(panneService.isPanneActive()).isFalse();
    }

    @Test
    void testInexistentUrlReturns404Not500() throws Exception {
        mockMvc.perform(get("/nimporte-quoi"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void testInexistentUrlWithJsonAcceptReturns404Json() throws Exception {
        mockMvc.perform(get("/nimporte-quoi").header("Accept", "application/json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void testFaviconReturns200() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk());
    }

    @Test
    void testInexistentProductReturns404Not500() throws Exception {
        mockMvc.perform(get("/produits/99999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }
}
