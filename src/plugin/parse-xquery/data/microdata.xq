(:
    Parsing of microdata,
    incomplete, no handling of refs and ids and for some of the url elements.
 :)

declare function local:get-items($node) {
    if ($node[@itemscope]) then
        element itemscope {
            if ($node/@itemtype) then attribute itemtype {$node/@itemtype} else (),
            for $child in $node/*
            return local:properties($child)
        }
    else
        for $child in $node/*
        return local:get-items($child)
};

declare function local:properties($node) {
    if ($node[@itemprop]) then
        element itemprop {
            attribute itemprop {$node/@itemprop},
            if ($node[@itemscope]) then
                local:get-items($node)
            else if ($node instance of element(img)) then
                $node/@src/string()
            else if ($node instance of element(a) or $node instance of element(link)) then
                $node/@href/string()
            else if ($node instance of element(meta)) then
                $node/@content/string()
            else
                $node/string()
        }
    else
        for $child in $node/*
        return local:properties($child)
};

local:get-items(.)