package com.electroshop.dto;

import com.electroshop.model.CompanySettings;

/**
 * Public, safe subset of the company profile — the contact details shown in the
 * storefront footer. Deliberately excludes fiscal/banking fields that are only
 * relevant on invoices.
 */
public record CompanyPublicDto(
        String legalName,
        String phone,
        String email,
        String website,
        String address,
        String city,
        String county,
        String country
) {
    public static CompanyPublicDto from(CompanySettings c) {
        if (c == null) {
            return new CompanyPublicDto(null, null, null, null, null, null, null, null);
        }
        return new CompanyPublicDto(
                c.getLegalName(),
                c.getPhone(),
                c.getEmail(),
                c.getWebsite(),
                c.getAddress(),
                c.getCity(),
                c.getCounty(),
                c.getCountry()
        );
    }
}
