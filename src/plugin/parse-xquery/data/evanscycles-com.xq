(: Parsing evancycles.com product page :)
let $url := 'http://www.evanscycles.com/products/specialized/p1-2010-jump-bike-ec020344',
    $doc := .,
    $domain := 'www.evanscycles.com',
    $specs := $doc//*[@id='product_features']

return element product {
    element type {'product'},
    element source {$domain},
    element brand {replace($url,"^.*/products/([^/]*).*$","$1")},
    element product_name {$doc//h1/string()},
    element source_product_id {$doc//*[@class='sku_code']/string()},
    element category {
        for $category in $doc//*[@id='breadcrumb']/li/a/string()
        return element item {$category}
    },
    element description {$doc//*[@id='product_description']/string()},
    element spec {
        for $feature at $pos in $specs/dt/string()
        let $value := $specs/dd[$pos]/string()
        return element item {attribute feature {replace($feature,':$','')}, attribute value {$value}}
    },
    element url {$url},
    element image_url {resolve-uri($doc//*[@id='main_product_image']/@src/string(),$url)},
    element created_at {current-dateTime()}
}